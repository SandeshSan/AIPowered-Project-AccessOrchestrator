package com.accessorchestrator.service;

import com.accessorchestrator.domain.AccessStatus;
import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.RequestItemStatus;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import com.accessorchestrator.dto.AccessRequestDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalPreviewDto;
import com.accessorchestrator.dto.RemovalDtos.RemovalResultDto;
import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.AccessRequestStatus;
import com.accessorchestrator.iga.IgaAccessRequest;
import com.accessorchestrator.iga.IgaIntegrationException;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectMemberRepository;
import com.accessorchestrator.repository.ProjectRepository;
import com.accessorchestrator.repository.UserAccessRepository;
import com.accessorchestrator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Removal rules: who may remove whom, what is revoked (project access minus default), pending requests are
 * withdrawn, and access is only marked REVOKED once the IGA completes the revocation.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectRemovalServiceTest {

    @Autowired private ProjectRemovalService removal;
    @Autowired private AccessRequestService accessRequests;
    @Autowired private DashboardService dashboard;
    @Autowired private ProjectRepository projects;
    @Autowired private EntitlementRepository entitlements;
    @Autowired private UserRepository users;
    @Autowired private ProjectMemberRepository members;
    @Autowired private UserAccessRepository userAccess;
    @Autowired private TransactionTemplate tx;
    @MockitoBean private IgaProvider iga;

    private long novatech;

    @BeforeEach
    void setUp() {
        novatech = projectId("NOVATECH");
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-20001", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    // --- preview ----------------------------------------------------------------------------------

    @Test
    void managerPreviewRevokesAllProjectAccess() {
        RemovalPreviewDto p = removal.preview("NT10020", "NT10042", novatech);

        assertThat(p.employee().name()).isEqualTo("Asha");
        assertThat(p.authorityBasis()).isEqualTo("direct report");
        assertThat(p.selfRemoval()).isFalse();
        assertThat(p.canProceed()).isTrue();
        assertThat(p.toRevoke()).extracting(r -> r.entitlement().entitlementCode())
                .containsExactly("GCP_NOVATECH_DEV", "NOVATECH_DEV", "NOVATECH_JIRA");
        assertThat(p.keptDefault()).isEmpty(); // Novatech uses no company-wide DEFAULT entitlements
    }

    @Test
    void plainEmployeeMayPreviewTheirOwnRemoval() {
        RemovalPreviewDto p = removal.preview("NT10036", "NT10036", novatech);

        assertThat(p.selfRemoval()).isTrue();
        assertThat(p.toRevoke()).extracting(r -> r.entitlement().entitlementCode())
                .containsExactly("GCP_NOVATECH_DEV", "NOVATECH_DEV");
    }

    @Test
    void whoMayRemoveWhom() {
        // plain employee removing someone else
        assertThatThrownBy(() -> removal.preview("NT10036", "NT10042", novatech))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("does not report to you");
        // a manager, but not Asha's (Priya manages only David)
        assertThatThrownBy(() -> removal.preview("NT10051", "NT10042", novatech))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("Asha (NT10042) does not report to you");
        // line manager can act on any of the report's projects, not just ones she is on
        assertThat(removal.preview("NT10020", "NT10042", projectId("ORION")).authorityBasis()).isEqualTo("direct report");
        assertThat(removal.preview("NT10020", "NT10051", projectId("HELIOS")).canProceed()).isTrue();
        // direct manager of David, and Mei two levels up
        assertThat(removal.preview("NT10051", "NT10070", projectId("ATLAS")).authorityBasis()).isEqualTo("direct report");
        assertThat(removal.preview("NT10020", "NT10070", projectId("ATLAS")).authorityBasis())
                .isEqualTo("indirect report (2 levels down)");
        // admin may remove anyone from any project
        assertThat(removal.preview("NT10001", "NT10058", projectId("HELIOS")).canProceed()).isTrue();
    }

    @Test
    void targetMustBeAnActiveMember() {
        assertThatThrownBy(() -> removal.preview("NT10020", "NT10036", projectId("ORION")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not an active member");
    }

    @Test
    void adminRemovalKeepsDefaultAccessOnHelios() {
        RemovalPreviewDto p = removal.preview("NT10001", "NT10051", projectId("HELIOS"));

        assertThat(p.toRevoke()).extracting(r -> r.entitlement().entitlementCode()).containsExactly("HELIOS_DEV");
        assertThat(p.keptDefault()).extracting(k -> k.entitlement().entitlementCode())
                .containsExactlyInAnyOrder("JIRA_USER", "CORP_VPN");
    }

    // --- removal ----------------------------------------------------------------------------------

    @Test
    void removalEndsMembershipAndSubmitsRevocationForApproval() {
        RemovalResultDto r = removal.remove("NT10020", "NT10042", novatech, "Moved to Helios Mobile");

        AccessRequestDto revocation = r.revocationRequest();
        assertThat(revocation.type()).isEqualTo(RequestType.REVOKE);
        assertThat(revocation.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        assertThat(revocation.igaRequestId()).isEqualTo("REQ-20001");
        assertThat(revocation.createdBy()).isEqualTo("NT10020");
        assertThat(revocation.justification()).isEqualTo("Moved to Helios Mobile");
        assertThat(revocation.items()).extracting(i -> i.entitlement().entitlementCode())
                .containsExactly("GCP_NOVATECH_DEV", "NOVATECH_DEV", "NOVATECH_JIRA");
        assertThat(revocation.lifecycle()).extracting(s -> s.label()).containsExactly(
                "Revocation Requested", "Pending Approval", "Approved", "Revoking", "Revoked");

        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(iga).createAccessRequest(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(RequestType.REVOKE);
        assertThat(sent.getValue().justification()).isEqualTo("Moved to Helios Mobile");

        // Membership ended for audit; the dashboard no longer lists Novatech
        tx.executeWithoutResult(s -> {
            var m = members.findAll().stream().filter(x -> x.getUser().getUserId().equals("NT10042")
                    && x.getProject().getProjectCode().equals("NOVATECH")).findFirst().orElseThrow();
            assertThat(m.getStatus()).isEqualTo(MembershipStatus.REMOVED);
            assertThat(m.getRemovedBy()).isEqualTo("NT10020");
            assertThat(m.getRemovalReason()).isEqualTo("Moved to Helios Mobile");
            assertThat(m.getRemovedAt()).isNotNull();
        });
        assertThat(dashboard.getMyProjects("NT10042")).extracting(p -> p.project().projectName())
                .containsExactly("Orion");

        // Access is untouched until the IGA reports the revocation complete
        assertThat(activeCodes("NT10042")).contains("NOVATECH_DEV", "GCP_NOVATECH_DEV");
    }

    @Test
    void accessIsRevokedOnlyWhenTheIgaCompletes() {
        AccessRequestDto revocation = removal.remove("NT10020", "NT10042", novatech, "Moved to Helios").revocationRequest();

        when(iga.getRequestStatus("REQ-20001"))
                .thenReturn(new AccessRequestStatus("REQ-20001", RequestStatus.PROVISIONED, Instant.now(), null));
        AccessRequestDto synced = accessRequests.syncStatus(revocation.requestId());

        assertThat(synced.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(RequestItemStatus.REVOKED));
        assertThat(synced.lifecycle().get(4).label()).isEqualTo("Revoked");
        assertThat(activeCodes("NT10042")).doesNotContain("NOVATECH_DEV", "GCP_NOVATECH_DEV", "NOVATECH_JIRA")
                .contains("ORION_DEV"); // other projects untouched
    }

    @Test
    void plainEmployeeCanRemoveThemselves() {
        RemovalResultDto r = removal.remove("NT10036", "NT10036", novatech, "Rolled off the project");

        assertThat(r.revocationRequest().createdBy()).isEqualTo("NT10036");
        assertThat(dashboard.getMyProjects("NT10036")).isEmpty();
    }

    @Test
    void reasonIsRequired() {
        assertThatThrownBy(() -> removal.remove("NT10020", "NT10042", novatech, "  "))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void pendingGrantRequestsForTheProjectAreCancelled() {
        accessRequests.requestAccess(new RequestAccessCommand("NT10036", novatech,
                List.of(entitlements.findByEntitlementCode("NOVATECH_VPN").orElseThrow().getId()), "test"));
        doReturn(new AccessRequestResponse("REQ-20002", RequestStatus.PENDING_APPROVAL, "ok"))
                .when(iga).createAccessRequest(any());
        when(iga.cancelRequest(eq("REQ-20001"), anyString(), anyString()))
                .thenReturn(new AccessRequestStatus("REQ-20001", RequestStatus.CANCELLED, Instant.now(), null));

        assertThat(removal.preview("NT10036", "NT10036", novatech).requestsToCancel())
                .extracting(AccessRequestDto::igaRequestId).containsExactly("REQ-20001");

        RemovalResultDto r = removal.remove("NT10036", "NT10036", novatech, "Rolled off the project");

        verify(iga).cancelRequest(eq("REQ-20001"), eq("NT10036"), anyString());
        AccessRequestDto cancelled = accessRequests.getStatusForUser("REQ-20001", "NT10036");
        assertThat(cancelled.status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(cancelled.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(RequestItemStatus.CANCELLED));
        assertThat(r.cancelledRequests()).hasSize(1);
        assertThat(r.revocationRequest().igaRequestId()).isEqualTo("REQ-20002");
    }

    @Test
    void grantAlreadyProvisioningBlocksTheRemoval() {
        AccessRequestDto grant = accessRequests.requestAccess(new RequestAccessCommand("NT10036", novatech,
                List.of(entitlements.findByEntitlementCode("NOVATECH_VPN").orElseThrow().getId()), "test"));
        when(iga.getRequestStatus("REQ-20001"))
                .thenReturn(new AccessRequestStatus("REQ-20001", RequestStatus.PROVISIONING, Instant.now(), null));
        accessRequests.syncStatus(grant.requestId());

        RemovalPreviewDto p = removal.preview("NT10036", "NT10036", novatech);
        assertThat(p.canProceed()).isFalse();
        assertThat(p.blockers()).singleElement().asString().contains("REQ-20001");
        assertThatThrownBy(() -> removal.remove("NT10036", "NT10036", novatech, "Rolled off"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void igaOutageLeavesTheMembershipActive() {
        when(iga.createAccessRequest(any())).thenThrow(new IgaIntegrationException("connection refused", null));

        assertThatThrownBy(() -> removal.remove("NT10020", "NT10042", novatech, "Moved to Helios"))
                .isInstanceOf(IgaIntegrationException.class);
        assertThat(dashboard.getMyProjects("NT10042")).extracting(p -> p.project().projectName())
                .contains("Novatech");
    }

    @Test
    void membersAreVisibleToAdminsOnly() {
        assertThat(removal.members("NT10001", novatech)).extracting(m -> m.user().userId())
                .contains("NT10036", "NT10042", "NT10020");
        assertThatThrownBy(() -> removal.members("NT10020", novatech)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> removal.members("NT10036", novatech)).isInstanceOf(ForbiddenException.class);
    }

    private long projectId(String code) {
        return projects.findByProjectCodeIgnoreCase(code).orElseThrow().getId();
    }

    private List<String> activeCodes(String userId) {
        return tx.execute(s -> userAccess.findByUser_IdAndStatus(users.findByUserIdIgnoreCase(userId).orElseThrow().getId(),
                AccessStatus.ACTIVE).stream().map(ua -> ua.getEntitlement().getEntitlementCode()).toList());
    }
}
