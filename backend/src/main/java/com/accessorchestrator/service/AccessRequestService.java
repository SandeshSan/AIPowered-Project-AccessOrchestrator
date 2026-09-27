package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessRequest;
import com.accessorchestrator.domain.AccessRequestItem;
import com.accessorchestrator.domain.AccessSource;
import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.Entitlement;
import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.ProjectAccess;
import com.accessorchestrator.domain.ProjectStatus;
import com.accessorchestrator.domain.RequestItemStatus;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserAccess;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.CreateAccessRequestRequest;
import com.accessorchestrator.dto.DtoMapper;
import com.accessorchestrator.dto.OpenRequestDto;
import com.accessorchestrator.dto.ProjectAccessDto;
import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.AccessRequestStatus;
import com.accessorchestrator.iga.IgaAccessRequest;
import com.accessorchestrator.iga.IgaIntegrationException;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.AccessRequestItemRepository;
import com.accessorchestrator.repository.AccessRequestRepository;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectAccessRepository;
import com.accessorchestrator.repository.UserAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Access request lifecycle on the orchestrator side.
 * <ul>
 *   <li>Items are always derived from {@link AccessAnalysisService}; callers may narrow the set but can never
 *       add entitlements the user is not actually missing.</li>
 *   <li>Submission hands the request to the {@link IgaProvider}, which owns approval and provisioning.</li>
 *   <li>Sync mirrors the IGA's status back. Access is recorded as ACTIVE only after the IGA reports
 *       PROVISIONED; nothing here grants access on its own authority.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<RequestStatus> OPEN_AT_IGA = EnumSet.of(RequestStatus.SUBMITTED,
            RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED, RequestStatus.PROVISIONING);
    /** Not yet provisioning, so still withdrawable. */
    private static final Set<RequestStatus> CANCELLABLE = EnumSet.of(RequestStatus.DRAFT, RequestStatus.SUBMITTED,
            RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED);

    private final AccessAnalysisService accessAnalysisService;
    private final UserService userService;
    private final ProjectService projectService;
    private final EntitlementRepository entitlementRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final UserAccessRepository userAccessRepository;
    private final AccessRequestItemRepository accessRequestItemRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final IgaProvider igaProvider;

    public AccessRequestService(AccessAnalysisService accessAnalysisService,
                                UserService userService,
                                ProjectService projectService,
                                EntitlementRepository entitlementRepository,
                                AccessRequestRepository accessRequestRepository,
                                UserAccessRepository userAccessRepository,
                                AccessRequestItemRepository accessRequestItemRepository,
                                ProjectAccessRepository projectAccessRepository,
                                IgaProvider igaProvider) {
        this.accessAnalysisService = accessAnalysisService;
        this.userService = userService;
        this.projectService = projectService;
        this.entitlementRepository = entitlementRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.userAccessRepository = userAccessRepository;
        this.accessRequestItemRepository = accessRequestItemRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.igaProvider = igaProvider;
    }

    @Transactional
    public AccessRequestDto createDraftRequest(CreateAccessRequestRequest command) {
        User user = userService.findUser(command.userId());
        Project project = projectService.findProject(command.projectId());

        List<ProjectAccessDto> missing = accessAnalysisService.calculateMissingAccess(user.getUserId(), project.getId());
        List<ProjectAccessDto> toRequest = select(missing, command.entitlementCodes());

        String createdBy = StringUtils.hasText(command.createdBy()) ? command.createdBy() : user.getUserId();
        AccessRequest request = new AccessRequest(newRequestId(), user, project, RequestStatus.DRAFT, createdBy);
        for (ProjectAccessDto pa : toRequest) {
            Entitlement entitlement = entitlementRepository.getReferenceById(pa.entitlement().id());
            request.addItem(new AccessRequestItem(entitlement, RequestItemStatus.PENDING, pa.reason()));
        }

        return DtoMapper.toDto(accessRequestRepository.save(request));
    }

    public AccessRequestDto getRequest(String requestId) {
        return DtoMapper.toDto(find(requestId));
    }

    /** Requests {@code userId} started for someone else (e.g. removals), newest first. */
    public List<AccessRequestDto> listRequestsCreatedBy(String userId) {
        User user = userService.findUser(userId);
        return accessRequestRepository.findByCreatedByIgnoreCaseOrderByCreatedAtDesc(user.getUserId()).stream()
                .filter(r -> !r.getUser().getUserId().equalsIgnoreCase(user.getUserId()))
                .map(DtoMapper::toDto)
                .toList();
    }

    /** Newest first. */
    public List<AccessRequestDto> listRequestsForUser(String userId) {
        User user = userService.findUser(userId);
        return accessRequestRepository.findByUser_UserIdIgnoreCaseOrderByCreatedAtDesc(user.getUserId()).stream()
                .map(DtoMapper::toDto)
                .toList();
    }

    /**
     * The user's confirmation step: sends a DRAFT to the IGA. Items are re-checked against the current gap
     * so access granted since the draft was created is never requested twice.
     */
    @Transactional
    public AccessRequestDto submit(String requestId) {
        AccessRequest request = find(requestId);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new BusinessRuleException("Only DRAFT requests can be submitted; " + requestId + " is "
                    + request.getStatus());
        }

        Set<String> stillMissing = accessAnalysisService
                .calculateMissingAccess(request.getUser().getUserId(), request.getProject().getId()).stream()
                .map(pa -> pa.entitlement().entitlementCode())
                .collect(Collectors.toSet());
        List<String> alreadyHeld = request.getItems().stream()
                .map(i -> i.getEntitlement().getEntitlementCode())
                .filter(code -> !stillMissing.contains(code))
                .toList();
        if (!alreadyHeld.isEmpty()) {
            throw new BusinessRuleException("User already holds " + alreadyHeld + "; create a new request");
        }
        ensureNoOpenRequests(request.getUser(),
                request.getItems().stream().map(i -> i.getEntitlement().getId()).toList());

        AccessRequestResponse response = igaProvider.createAccessRequest(toIgaRequest(request));
        request.setIgaRequestId(response.igaRequestId());
        request.setStatus(response.status());
        log.info("Submitted {} to IGA [{}] as {} ({})", requestId, igaProvider.name(), response.igaRequestId(),
                response.status());
        return DtoMapper.toDto(request);
    }

    /**
     * Create-and-submit in one step, for callers that have already obtained the user's confirmation (the AI
     * agent). Checks, in order: user active, project active, entitlements exist, entitlements belong to the
     * project's access profile, not already held, no request already in progress for them. Then persists the
     * request, submits it to the IGA and stores the IGA's request id. If the IGA call fails the request is
     * kept as FAILED for audit and the error is rethrown.
     */
    @Transactional(noRollbackFor = IgaIntegrationException.class)
    public AccessRequestDto requestAccess(RequestAccessCommand command) {
        User user = userService.findUser(command.userId());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("User " + user.getUserId() + " is not active");
        }
        Project project = projectService.findProject(command.projectId());
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException("Project " + project.getProjectName() + " is not active");
        }

        Set<Long> ids = new LinkedHashSet<>(command.entitlementIds());
        Map<Long, Entitlement> entitlements = entitlementRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Entitlement::getId, Function.identity()));
        List<Long> unknown = ids.stream().filter(id -> !entitlements.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new ResourceNotFoundException("Entitlement", unknown);
        }

        Map<Long, ProjectAccess> profile = projectAccessRepository.findByProject_Id(project.getId()).stream()
                .collect(Collectors.toMap(pa -> pa.getEntitlement().getId(), Function.identity()));
        List<String> outsideProfile = ids.stream().filter(id -> !profile.containsKey(id))
                .map(id -> entitlements.get(id).getEntitlementCode()).toList();
        if (!outsideProfile.isEmpty()) {
            throw new BusinessRuleException("Not part of the " + project.getProjectName() + " access profile: "
                    + outsideProfile);
        }

        Set<Long> held = userAccessRepository.findByUser_IdAndStatus(user.getId(), AccessStatus.ACTIVE).stream()
                .map(ua -> ua.getEntitlement().getId()).collect(Collectors.toSet());
        List<String> alreadyHeld = ids.stream().filter(held::contains)
                .map(id -> entitlements.get(id).getEntitlementCode()).toList();
        if (!alreadyHeld.isEmpty()) {
            throw new BusinessRuleException("User already holds " + alreadyHeld);
        }
        ensureNoOpenRequests(user, ids);

        String createdBy = StringUtils.hasText(command.createdBy()) ? command.createdBy() : user.getUserId();
        AccessRequest request = new AccessRequest(newRequestId(), user, project, RequestStatus.DRAFT, createdBy);
        ids.forEach(id -> request.addItem(
                new AccessRequestItem(entitlements.get(id), RequestItemStatus.PENDING, profile.get(id).getReason())));
        accessRequestRepository.save(request);

        try {
            AccessRequestResponse response = igaProvider.createAccessRequest(toIgaRequest(request));
            request.setIgaRequestId(response.igaRequestId());
            request.setStatus(response.status());
            log.info("Request {} for {} submitted to IGA [{}] as {} ({})", request.getRequestId(),
                    user.getUserId(), igaProvider.name(), response.igaRequestId(), response.status());
        } catch (IgaIntegrationException e) {
            request.setStatus(RequestStatus.FAILED);
            request.getItems().forEach(i -> i.setStatus(RequestItemStatus.FAILED));
            throw e;
        }
        return DtoMapper.toDto(request);
    }

    /** Entitlements the user has in requests that are still in progress at the IGA. */
    public List<OpenRequestDto> findOpenRequests(String userId) {
        User user = userService.findUser(userId);
        return accessRequestItemRepository.findOpenItems(user.getId(), OPEN_AT_IGA).stream()
                .filter(o -> o.type() == RequestType.GRANT)
                .map(o -> new OpenRequestDto(o.entitlementId(), o.entitlementCode(), o.requestId(),
                        o.igaRequestId(), o.status()))
                .toList();
    }

    /**
     * Looks a request up by the orchestrator id or the IGA id, only if it belongs to {@code userId}, and
     * refreshes it from the IGA first when it is still in progress.
     */
    @Transactional
    public AccessRequestDto getStatusForUser(String reference, String userId) {
        AccessRequest request = accessRequestRepository.findByRequestId(reference)
                .or(() -> accessRequestRepository.findByIgaRequestId(reference))
                .filter(r -> r.getUser().getUserId().equalsIgnoreCase(userId)
                        || r.getCreatedBy().equalsIgnoreCase(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Access request", reference));
        if (request.getIgaRequestId() != null && !request.getStatus().isTerminal()) {
            apply(request, igaProvider.getRequestStatus(request.getIgaRequestId()));
        }
        return DtoMapper.toDto(request);
    }

    /**
     * Withdraws the user's grant requests for a project that have not started provisioning: drafts locally, the
     * rest at the IGA too. Returns the cancelled requests.
     */
    @Transactional
    public List<AccessRequestDto> cancelOpenGrants(User user, Project project, String actorUserId, String reason) {
        List<AccessRequest> open = accessRequestRepository.findByUser_IdAndProject_IdAndTypeAndStatusIn(user.getId(),
                project.getId(), RequestType.GRANT, CANCELLABLE);
        for (AccessRequest request : open) {
            if (request.getIgaRequestId() != null) {
                igaProvider.cancelRequest(request.getIgaRequestId(), actorUserId, reason);
            }
            request.setStatus(RequestStatus.CANCELLED);
            request.getItems().forEach(i -> i.setStatus(RequestItemStatus.CANCELLED));
            log.info("Cancelled {} ({}) for {} on {}: {}", request.getRequestId(), request.getIgaRequestId(),
                    user.getUserId(), project.getProjectCode(), reason);
        }
        return open.stream().map(DtoMapper::toDto).toList();
    }

    /** Grant requests for the project that are already provisioning and can no longer be withdrawn. */
    public List<AccessRequestDto> findProvisioningGrants(User user, Project project) {
        return accessRequestRepository.findByUser_IdAndProject_IdAndTypeAndStatusIn(user.getId(), project.getId(),
                        RequestType.GRANT, EnumSet.of(RequestStatus.PROVISIONING)).stream()
                .map(DtoMapper::toDto).toList();
    }

    /** Grant requests for the project that {@link #cancelOpenGrants} would withdraw. */
    public List<AccessRequestDto> findCancellableGrants(User user, Project project) {
        return accessRequestRepository.findByUser_IdAndProject_IdAndTypeAndStatusIn(user.getId(), project.getId(),
                RequestType.GRANT, CANCELLABLE).stream().map(DtoMapper::toDto).toList();
    }

    /**
     * Sends a REVOKE request for the given held entitlements to the IGA, which approves and deprovisions them.
     * Access is marked REVOKED only when the IGA reports completion (see {@link #apply}). IGA errors propagate so
     * the caller's transaction rolls back.
     */
    @Transactional
    public AccessRequestDto createRevocation(User user, Project project, List<Entitlement> entitlements,
                                             Map<Long, String> reasons, String actorUserId, String justification) {
        AccessRequest request = new AccessRequest(newRequestId(), RequestType.REVOKE, user, project,
                RequestStatus.DRAFT, actorUserId);
        request.setJustification(justification);
        entitlements.forEach(e -> request.addItem(new AccessRequestItem(e, RequestItemStatus.PENDING,
                reasons.getOrDefault(e.getId(), "Removed from project " + project.getProjectName()))));
        accessRequestRepository.save(request);

        AccessRequestResponse response = igaProvider.createAccessRequest(toIgaRequest(request));
        request.setIgaRequestId(response.igaRequestId());
        request.setStatus(response.status());
        log.info("Revocation {} for {} on {} submitted to IGA [{}] as {} ({})", request.getRequestId(),
                user.getUserId(), project.getProjectCode(), igaProvider.name(), response.igaRequestId(),
                response.status());
        return DtoMapper.toDto(request);
    }

    private void ensureNoOpenRequests(User user, Collection<Long> entitlementIds) {
        List<String> inFlight = accessRequestItemRepository.findOpenItems(user.getId(), OPEN_AT_IGA).stream()
                .filter(o -> entitlementIds.contains(o.entitlementId()))
                .map(o -> o.entitlementCode() + " (" + (o.igaRequestId() != null ? o.igaRequestId() : o.requestId())
                        + ", " + o.status() + ")")
                .toList();
        if (!inFlight.isEmpty()) {
            throw new BusinessRuleException("Already requested and still in progress: " + inFlight);
        }
    }

    /** Pulls the latest status from the IGA and mirrors it locally. Terminal requests are left as they are. */
    @Transactional
    public AccessRequestDto syncStatus(String requestId) {
        AccessRequest request = find(requestId);
        if (request.getIgaRequestId() == null) {
            throw new BusinessRuleException("Request " + requestId + " has not been submitted to the IGA");
        }
        if (!request.getStatus().isTerminal()) {
            apply(request, igaProvider.getRequestStatus(request.getIgaRequestId()));
        }
        return DtoMapper.toDto(request);
    }

    /** Ids of submitted requests that are still moving through the IGA workflow. */
    public List<String> findOpenRequestIds() {
        return accessRequestRepository.findOpenRequestIds(OPEN_AT_IGA);
    }

    private void apply(AccessRequest request, AccessRequestStatus igaStatus) {
        RequestStatus next = igaStatus.status();
        if (next == request.getStatus()) {
            return;
        }
        log.info("Request {} ({}): {} -> {}", request.getRequestId(), request.getIgaRequestId(),
                request.getStatus(), next);
        request.setStatus(next);

        boolean revoke = request.getType() == RequestType.REVOKE;
        RequestItemStatus itemStatus = switch (next) {
            case APPROVED, PROVISIONING -> RequestItemStatus.APPROVED;
            case REJECTED -> RequestItemStatus.REJECTED;
            case CANCELLED -> RequestItemStatus.CANCELLED;
            case PROVISIONED -> revoke ? RequestItemStatus.REVOKED : RequestItemStatus.PROVISIONED;
            case FAILED -> RequestItemStatus.FAILED;
            default -> null;
        };
        if (itemStatus != null) {
            request.getItems().forEach(i -> i.setStatus(itemStatus));
        }
        if (next == RequestStatus.PROVISIONED) {
            if (revoke) {
                recordRevokedAccess(request);
            } else {
                recordProvisionedAccess(request);
            }
        }
    }

    /** Reflects what the IGA provisioned: reactivates revoked/expired rows or inserts new ACTIVE ones. */
    private void recordProvisionedAccess(AccessRequest request) {
        User user = request.getUser();
        LocalDate today = LocalDate.now();
        for (AccessRequestItem item : request.getItems()) {
            Entitlement entitlement = item.getEntitlement();
            userAccessRepository.findByUser_IdAndEntitlement_Id(user.getId(), entitlement.getId())
                    .ifPresentOrElse(
                            existing -> existing.grant(today, AccessSource.IGA),
                            () -> userAccessRepository.save(
                                    new UserAccess(user, entitlement, AccessStatus.ACTIVE, today, AccessSource.IGA)));
        }
    }

    /** Reflects what the IGA deprovisioned: the user's ACTIVE rows for those entitlements become REVOKED. */
    private void recordRevokedAccess(AccessRequest request) {
        User user = request.getUser();
        for (AccessRequestItem item : request.getItems()) {
            userAccessRepository.findByUser_IdAndEntitlement_Id(user.getId(), item.getEntitlement().getId())
                    .filter(ua -> ua.getStatus() == AccessStatus.ACTIVE)
                    .ifPresent(ua -> ua.setStatus(AccessStatus.REVOKED));
        }
    }

    private AccessRequest find(String requestId) {
        return accessRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request", requestId));
    }

    private static IgaAccessRequest toIgaRequest(AccessRequest request) {
        User user = request.getUser();
        Project project = request.getProject();
        List<IgaAccessRequest.Item> items = request.getItems().stream()
                .map(i -> new IgaAccessRequest.Item(i.getEntitlement().getApplication(),
                        i.getEntitlement().getEntitlementCode(), i.getReason()))
                .toList();
        String justification = request.getJustification() != null ? request.getJustification()
                : "Standard access for project " + project.getProjectName();
        return new IgaAccessRequest(request.getRequestId(), request.getType(), user.getUserId(),
                request.getCreatedBy(), user.getEmail(), project.getProjectCode(), justification, items);
    }

    private static List<ProjectAccessDto> select(List<ProjectAccessDto> missing, List<String> requestedCodes) {
        if (missing.isEmpty()) {
            throw new BusinessRuleException("User already has all required access for this project");
        }
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return missing;
        }
        Set<String> wanted = new LinkedHashSet<>(requestedCodes);
        Set<String> notMissing = new LinkedHashSet<>(wanted);
        missing.forEach(pa -> notMissing.remove(pa.entitlement().entitlementCode()));
        if (!notMissing.isEmpty()) {
            throw new BusinessRuleException(
                    "Entitlements are not missing required access for this project: " + notMissing);
        }
        return missing.stream().filter(pa -> wanted.contains(pa.entitlement().entitlementCode())).toList();
    }

    private static String newRequestId() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "REQ-" + LocalDate.now().format(ID_DATE) + "-" + suffix;
    }
}
