package com.accessorchestrator.service;

import com.accessorchestrator.domain.RequestItemStatus;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.UserStatus;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.CreateAccessRequestRequest;
import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ResourceNotFoundException;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaIntegrationException;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.AccessRequestRepository;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectRepository;
import com.accessorchestrator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The nine checks of the create-and-submit flow used by the agent. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RequestAccessServiceTest {

    @Autowired private AccessRequestService service;
    @Autowired private ProjectRepository projects;
    @Autowired private EntitlementRepository entitlements;
    @Autowired private UserRepository users;
    @Autowired private AccessRequestRepository requests;
    @MockitoBean private IgaProvider iga;

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-10173", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void createsLocalRequestSubmitsToIgaAndStoresIgaId() {
        AccessRequestDto r = service.requestAccess(cmd("NT10036", "NOVATECH_JIRA", "NOVATECH_DB_READ", "NOVATECH_VPN"));

        assertThat(r.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        assertThat(r.igaRequestId()).isEqualTo("REQ-10173");
        assertThat(r.createdBy()).isEqualTo("access-agent");
        assertThat(r.items()).extracting(i -> i.entitlement().entitlementCode())
                .containsExactly("NOVATECH_JIRA", "NOVATECH_DB_READ", "NOVATECH_VPN");
        assertThat(r.items()).allSatisfy(i -> assertThat(i.reason()).startsWith("Required"));
        assertThat(requests.findByIgaRequestId("REQ-10173")).isPresent();
    }

    @Test
    void unknownUserOrProject() {
        assertThatThrownBy(() -> service.requestAccess(cmd("NOPE", "NOVATECH_VPN")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.requestAccess(
                new RequestAccessCommand("NT10036", 999L, List.of(id("NOVATECH_VPN")), "t")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inactiveUserIsRejected() {
        users.findByUserIdIgnoreCase("NT10036").ifPresent(u -> {
            u.setStatus(UserStatus.INACTIVE);
            users.save(u);
        });
        assertThatThrownBy(() -> service.requestAccess(cmd("NT10036", "NOVATECH_VPN")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not active");
    }

    @Test
    void unknownEntitlementIdIsRejected() {
        assertThatThrownBy(() -> service.requestAccess(
                new RequestAccessCommand("NT10036", novatechId, List.of(id("NOVATECH_VPN"), 9999L), "t")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("9999");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void entitlementOutsideProjectProfileIsRejected() {
        assertThatThrownBy(() -> service.requestAccess(cmd("NT10036", "ORION_DEV")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("access profile");
    }

    @Test
    void optionalProfileEntitlementCanBeRequested() {
        assertThat(service.requestAccess(cmd("NT10036", "NOVATECH_WIKI")).status())
                .isEqualTo(RequestStatus.PENDING_APPROVAL);
    }

    @Test
    void alreadyHeldEntitlementIsRejected() {
        assertThatThrownBy(() -> service.requestAccess(cmd("NT10036", "NOVATECH_DEV", "NOVATECH_VPN")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already holds [NOVATECH_DEV]");
    }

    @Test
    void duplicateActiveRequestIsRejected() {
        service.requestAccess(cmd("NT10036", "NOVATECH_VPN"));

        assertThatThrownBy(() -> service.requestAccess(cmd("NT10036", "NOVATECH_JIRA", "NOVATECH_VPN")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("NOVATECH_VPN (REQ-10173, PENDING_APPROVAL)");
        assertThat(service.findOpenRequests("NT10036")).extracting(o -> o.entitlementCode())
                .containsExactly("NOVATECH_VPN");
    }

    @Test
    void duplicateGuardAlsoAppliesToDraftSubmission() {
        AccessRequestDto draft = service.createDraftRequest(new CreateAccessRequestRequest("NT10036", novatechId,
                List.of("NOVATECH_VPN"), null));
        service.requestAccess(cmd("NT10036", "NOVATECH_VPN"));

        assertThatThrownBy(() -> service.submit(draft.requestId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Already requested");
    }

    @Test
    void igaFailureKeepsRequestAsFailedAndDoesNotBlockRetry() {
        when(iga.createAccessRequest(any())).thenThrow(new IgaIntegrationException("connection refused", null));

        assertThatThrownBy(() -> service.requestAccess(cmd("NT10036", "NOVATECH_VPN")))
                .isInstanceOf(IgaIntegrationException.class);
        assertThat(requests.findByUser_UserIdIgnoreCaseOrderByCreatedAtDesc("NT10036")).singleElement().satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo(RequestStatus.FAILED);
            assertThat(r.getIgaRequestId()).isNull();
        });
        assertThat(service.findOpenRequests("NT10036")).isEmpty();

        doReturn(new AccessRequestResponse("REQ-10174", RequestStatus.PENDING_APPROVAL, "ok"))
                .when(iga).createAccessRequest(any());
        assertThat(service.requestAccess(cmd("NT10036", "NOVATECH_VPN")).igaRequestId()).isEqualTo("REQ-10174");
    }

    @Test
    void statusLookupByIgaIdIsScopedToOwner() {
        service.requestAccess(cmd("NT10036", "NOVATECH_VPN"));
        when(iga.getRequestStatus("REQ-10173")).thenReturn(new com.accessorchestrator.iga.AccessRequestStatus(
                "REQ-10173", RequestStatus.APPROVED, java.time.Instant.now(), null));

        AccessRequestDto mine = service.getStatusForUser("REQ-10173", "NT10036");
        assertThat(mine.status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(mine.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(RequestItemStatus.APPROVED));

        assertThatThrownBy(() -> service.getStatusForUser("REQ-10173", "NT10042"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private RequestAccessCommand cmd(String userId, String... codes) {
        return new RequestAccessCommand(userId, novatechId, List.of(codes).stream().map(this::id).toList(),
                "access-agent");
    }

    private Long id(String code) {
        return entitlements.findByEntitlementCode(code).orElseThrow().getId();
    }
}
