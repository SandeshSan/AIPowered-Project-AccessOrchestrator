package com.accessorchestrator.agent;

import com.accessorchestrator.agent.AgentModels.AccessItem;
import com.accessorchestrator.agent.AgentModels.AccessRequestView;
import com.accessorchestrator.agent.AgentModels.ColleagueComparisonView;
import com.accessorchestrator.agent.AgentModels.ComparedItem;
import com.accessorchestrator.agent.AgentModels.InFlightItem;
import com.accessorchestrator.agent.AgentModels.MissingAccessView;
import com.accessorchestrator.agent.AgentModels.ProjectView;
import com.accessorchestrator.agent.AgentModels.UserView;
import com.accessorchestrator.dto.AccessComparisonResponse;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ColleagueComparisonDto;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ComparedEntitlement;
import com.accessorchestrator.dto.ComparisonStatus;
import com.accessorchestrator.dto.EntitlementDto;
import com.accessorchestrator.dto.OpenRequestDto;
import com.accessorchestrator.dto.ProjectDto;
import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.dto.UserDto;
import com.accessorchestrator.service.AccessAnalysisService;
import com.accessorchestrator.service.AccessAuthority;
import com.accessorchestrator.service.AccessRequestService;
import com.accessorchestrator.service.ColleagueAccessService;
import com.accessorchestrator.service.EntitlementService;
import com.accessorchestrator.service.ProjectService;
import com.accessorchestrator.service.UserService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only capabilities the AI agent has. Every tool delegates to the deterministic service layer; none of
 * them lets the model compute, grant or approve access. Identity comes from the {@link AgentTurn} in the
 * {@link ToolContext}, never from model-supplied text.
 */
@Component
public class AccessAgentTools {

    static final String CREATED_BY = "access-agent";

    private final UserService userService;
    private final ProjectService projectService;
    private final EntitlementService entitlementService;
    private final AccessAnalysisService accessAnalysisService;
    private final AccessRequestService accessRequestService;
    private final AccessAuthority accessAuthority;
    private final ColleagueAccessService colleagueAccessService;

    public AccessAgentTools(UserService userService, ProjectService projectService,
                            EntitlementService entitlementService, AccessAnalysisService accessAnalysisService,
                            AccessRequestService accessRequestService, AccessAuthority accessAuthority,
                            ColleagueAccessService colleagueAccessService) {
        this.colleagueAccessService = colleagueAccessService;
        this.userService = userService;
        this.projectService = projectService;
        this.entitlementService = entitlementService;
        this.accessAnalysisService = accessAnalysisService;
        this.accessRequestService = accessRequestService;
        this.accessAuthority = accessAuthority;
    }

    @Tool(description = "Get the signed-in employee you are talking to (user id, name, role). "
            + "Call this first to identify the user.")
    public UserView getCurrentUser(ToolContext toolContext) {
        return run(toolContext, "getCurrentUser", turn -> toView(userService.getUser(turn.userId())));
    }

    @Tool(description = "Look up an employee's basic profile by user id.")
    public UserView getUser(@ToolParam(description = "Employee id, e.g. NT10036") String userId,
                            ToolContext toolContext) {
        return run(toolContext, "getUser", turn -> toView(userService.getUser(userId)));
    }

    @Tool(description = "Find a project by the name or code the user mentioned, tolerant of case and spacing "
            + "(e.g. 'NovaTech'). Returns the projectId needed by other tools.")
    public ProjectView findProject(@ToolParam(description = "Project name or code as the user wrote it")
                                   String projectName, ToolContext toolContext) {
        return run(toolContext, "findProject", turn -> {
            ProjectDto p = projectService.findByNameOrCode(projectName);
            return new ProjectView(p.id(), p.projectCode(), p.projectName(), p.description());
        });
    }

    @Tool(description = "List the entitlements required by a project's standard access profile.")
    public List<AccessItem> getRequiredProjectAccess(@ToolParam(description = "projectId from findProject")
                                                     Long projectId, ToolContext toolContext) {
        return run(toolContext, "getRequiredProjectAccess", turn -> accessAnalysisService.getRequiredAccess(projectId)
                .stream().map(pa -> toItem(pa.entitlement(), pa.reason())).toList());
    }

    @Tool(description = "List someone's currently active entitlements: the signed-in user's own, or (for managers) "
            + "those of a person who reports to them, up to three levels down. Admins may view anyone's. To compare "
            + "the signed-in user's access with anyone else's, use compareAccessWithColleague instead.")
    public List<AccessItem> getUserExistingAccess(
            @ToolParam(description = "User id: the signed-in user's, or one of their reportees'") String userId,
            ToolContext toolContext) {
        return run(toolContext, "getUserExistingAccess", turn -> {
            accessAuthority.requireCanViewAccess(turn.userId(), userId);
            return accessAnalysisService.getExistingAccess(userId).stream()
                    .map(ua -> toItem(ua.entitlement(), null)).toList();
        });
    }

    @Tool(description = "Compare the signed-in user's active access with a colleague's (anyone): what the colleague "
            + "has that the user doesn't, what the user has that the colleague doesn't, and how many they share. "
            + "Find the colleague's userId with findEmployee first. The comparison is done by the system; present "
            + "it as-is. It requests nothing.")
    public ColleagueComparisonView compareAccessWithColleague(
            @ToolParam(description = "The colleague's user id, from findEmployee") String colleagueUserId,
            ToolContext toolContext) {
        return run(toolContext, "compareAccessWithColleague", turn -> {
            ColleagueComparisonDto c = colleagueAccessService.compare(turn.userId(), colleagueUserId);
            List<ComparedItem> theirs = c.colleagueOnly().stream().map(AccessAgentTools::toItem).toList();
            List<ComparedItem> mine = c.youOnly().stream().map(AccessAgentTools::toItem).toList();
            return new ColleagueComparisonView(c.colleague().userId(), c.colleague().name(), c.fullDetail(),
                    theirs, mine, c.inCommonCount(), comparisonNextStep(c));
        });
    }

    @Tool(description = "Compare the signed-in user's access with a project's required access. Returns what "
            + "they already have, what is missing, what is already requested, and the entitlement ids that can be "
            + "requested. The calculation is done by the system; present it as-is.")
    public MissingAccessView calculateMissingAccess(
            @ToolParam(description = "The signed-in user's id") String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            ToolContext toolContext) {
        return run(toolContext, "calculateMissingAccess", turn -> {
            requireSelf(turn, userId);
            UserDto user = userService.getUser(userId);
            AccessComparisonResponse comparison = accessAnalysisService.compare(userId, projectId);

            List<AccessItem> have = comparison.items().stream()
                    .filter(i -> i.status() == ComparisonStatus.GRANTED)
                    .map(i -> toItem(i.entitlement(), i.reason())).toList();
            List<AccessItem> missing = comparison.missingAccess().stream()
                    .map(pa -> toItem(pa.entitlement(), pa.reason())).toList();

            Set<Long> missingIds = missing.stream().map(AccessItem::entitlementId).collect(Collectors.toSet());
            Map<Long, OpenRequestDto> inFlight = accessRequestService.findOpenRequests(userId).stream()
                    .filter(o -> missingIds.contains(o.entitlementId()))
                    .collect(Collectors.toMap(OpenRequestDto::entitlementId, Function.identity(), (a, b) -> a));
            List<InFlightItem> alreadyRequested = inFlight.values().stream()
                    .map(o -> new InFlightItem(o.entitlementCode(),
                            o.igaRequestId() != null ? o.igaRequestId() : o.requestId(), o.status()))
                    .toList();
            List<Long> requestable = missing.stream().map(AccessItem::entitlementId)
                    .filter(id -> !inFlight.containsKey(id)).toList();

            turn.conversation().propose(projectId, comparison.project(), new LinkedHashSet<>(requestable), turn.turn());

            MissingAccessView view = new MissingAccessView(user.userId(), user.name(), user.role(), projectId,
                    comparison.project(), have, missing, alreadyRequested, requestable,
                    comparison.verification().expression(), nextStep(missing, requestable));
            turn.analysis(view);
            return view;
        });
    }

    @Tool(description = "Get details of an entitlement by its code, e.g. NOVATECH_VPN.")
    public AccessItem getEntitlementDetails(@ToolParam(description = "Entitlement code") String entitlementCode,
                                            ToolContext toolContext) {
        return run(toolContext, "getEntitlementDetails",
                turn -> toItem(entitlementService.getByCode(entitlementCode), null));
    }

    @Tool(description = "Submit an access request for the signed-in user to the IGA system for manager approval. "
            + "Only call this after you have shown the missing access AND the user has explicitly confirmed in "
            + "their latest message. Use ids from requestableEntitlementIds of calculateMissingAccess.")
    public AccessRequestView createAccessRequest(
            @ToolParam(description = "The signed-in user's id") String userId,
            @ToolParam(description = "projectId from findProject") Long projectId,
            @ToolParam(description = "Entitlement ids to request (from requestableEntitlementIds)")
            List<Long> entitlementIds,
            ToolContext toolContext) {
        return run(toolContext, "createAccessRequest", turn -> {
            requireSelf(turn, userId);
            requireConfirmed(turn, projectId, entitlementIds);

            AccessRequestDto created = accessRequestService.requestAccess(
                    new RequestAccessCommand(turn.userId(), projectId, entitlementIds, CREATED_BY));
            turn.conversation().clearProposal();

            AccessRequestView view = toView(created);
            turn.createdRequest(view);
            return view;
        });
    }

    @Tool(description = "Get the current status of one of the signed-in user's access requests, by the request "
            + "id shown to the user (e.g. REQ-10173).")
    public AccessRequestView getAccessRequestStatus(@ToolParam(description = "Request id") String requestId,
                                                    ToolContext toolContext) {
        return run(toolContext, "getAccessRequestStatus",
                turn -> toView(accessRequestService.getStatusForUser(requestId, turn.userId())));
    }

    // --- guardrails -----------------------------------------------------------------------------------

    private static void requireSelf(AgentTurn turn, String userId) {
        if (userId == null || !userId.equalsIgnoreCase(turn.userId())) {
            throw new AgentGuardException("Refused: this tool only works with the signed-in user's own access. "
                    + "The signed-in user's id is " + turn.userId() + "; use that userId. Access cannot be requested "
                    + "for other people: to add someone else to a project use previewAddToProject / "
                    + "addEmployeeToProject; to remove them use previewProjectRemoval / removeEmployeeFromProject.");
        }
    }

    /**
     * Two independent keys: (1) the system itself showed these entitlements as requestable in an earlier turn,
     * and (2) the user's current message is an explicit confirmation.
     */
    private static void requireConfirmed(AgentTurn turn, Long projectId, List<Long> entitlementIds) {
        ConversationState.Proposal proposal = turn.conversation().proposal();
        boolean presentedEarlier = proposal != null
                && proposal.projectId().equals(projectId)
                && proposal.turn() < turn.turn()
                && entitlementIds != null && !entitlementIds.isEmpty()
                && proposal.submittable().containsAll(entitlementIds);
        if (!presentedEarlier && proposal != null && proposal.isNarrowed() && entitlementIds != null
                && proposal.entitlementIds().containsAll(entitlementIds)) {
            throw new AgentGuardException("Refused: the user selected only entitlementIds " + proposal.selectedIds()
                    + ". Call createAccessRequest with exactly those ids.");
        }
        if (!presentedEarlier) {
            throw new AgentGuardException("Refused: confirmation required. First call calculateMissingAccess, show "
                    + "the user what is missing and ask whether to submit the request. Only call "
                    + "createAccessRequest after the user confirms in a later message, using ids from "
                    + "requestableEntitlementIds.");
        }
        if (!ConfirmationPolicy.isAffirmative(turn.userMessage())) {
            throw new AgentGuardException("Refused: the user's latest message is not an explicit confirmation. "
                    + "Ask them clearly whether to submit the request.");
        }
    }

    // --- helpers --------------------------------------------------------------------------------------

    private static <T> T run(ToolContext toolContext, String tool, Function<AgentTurn, T> body) {
        return AgentTurn.run(toolContext, tool, body);
    }

    private static String nextStep(List<AccessItem> missing, List<Long> requestable) {
        if (missing.isEmpty()) {
            return "The user already has all required access. Tell them; do not create a request.";
        }
        if (requestable.isEmpty()) {
            return "Everything missing is already requested and in progress. Report those request ids and "
                    + "statuses; do not create another request.";
        }
        return "Show alreadyHave and missing, then ask: 'Would you like me to submit the missing access "
                + "requests?' Do not call createAccessRequest until the user confirms in a later message.";
    }

    private static String comparisonNextStep(ColleagueComparisonDto c) {
        String name = c.colleague().name();
        StringBuilder next = new StringBuilder("List what " + name + " has that the user doesn't, then what the user "
                + "has that " + name + " doesn't, grouped by application, and say how many they share. ");
        if (c.colleagueOnly().stream().anyMatch(ComparedEntitlement::restricted)) {
            next.append("Include every restricted item in the list as 'Restricted (high-risk access)' with its "
                    + "application; never guess or name the entitlement. ");
        }
        List<String> projects = c.colleagueOnly().stream().map(ComparedEntitlement::requestableProjectName)
                .filter(Objects::nonNull).distinct().toList();
        if (projects.isEmpty()) {
            next.append("None of the differences is part of the user's own project access, so do not offer to "
                    + "request them; for access a role needs beyond its project, the user should ask their manager.");
        } else {
            next.append("Items with requestableForProject are part of the required access of the user's project(s) ")
                    .append(projects)
                    .append(". Offer to check the user's access for that project; if they agree, call findProject and "
                            + "calculateMissingAccess for it and follow the normal confirmation flow. Never offer "
                            + "to request the other differences.");
        }
        return next.toString();
    }

    /** A restricted item gets an explicit label: with no name at all, the model tends to leave it out. */
    private static ComparedItem toItem(ComparedEntitlement e) {
        String name = e.restricted() ? "Restricted (high-risk access)" : e.entitlementName();
        return new ComparedItem(e.application(), e.entitlementCode(), name, e.riskLevel(),
                e.restricted(), e.requestableProjectId(), e.requestableProjectName(), e.note());
    }

    private static UserView toView(UserDto u) {
        return new UserView(u.userId(), u.name(), u.role(), u.department(), u.email());
    }

    private static AccessItem toItem(EntitlementDto e, String reason) {
        return new AccessItem(e.id(), e.application(), e.entitlementCode(), e.entitlementName(), e.riskLevel(),
                reason);
    }

    private static AccessRequestView toView(AccessRequestDto r) {
        List<AccessItem> items = r.items().stream().map(i -> toItem(i.entitlement(), i.reason())).toList();
        String shownId = r.igaRequestId() != null ? r.igaRequestId() : r.requestId();
        return new AccessRequestView(shownId, r.requestId(), r.type(), r.projectName(), r.status(),
                AgentModels.label(r.status(), r.type()), items);
    }
}
