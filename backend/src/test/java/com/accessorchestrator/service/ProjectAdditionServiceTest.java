package com.accessorchestrator.service;

import com.accessorchestrator.domain.MembershipStatus;
import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.dto.AdditionDtos.AdditionPreviewDto;
import com.accessorchestrator.dto.AdditionDtos.AdditionResultDto;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ForbiddenException;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaIntegrationException;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.ProjectMemberRepository;
import com.accessorchestrator.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Managers (and admins) add reportees to projects; missing required access is requested from the IGA for them. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectAdditionServiceTest {

    @Autowired private ProjectAdditionService addition;
    @Autowired private ProjectRemovalService removal;
    @Autowired private DashboardService dashboard;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMemberRepository members;
    @Autowired private TransactionTemplate tx;
    @MockitoBean private IgaProvider iga;

    @BeforeEach
    void setUp() {
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-30001", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void managerAddsReporteeAsMemberWithoutRequestingAccess() {
        AdditionPreviewDto p = addition.preview("NT10020", "NT10036", projectId("ATLAS"), null);
        assertThat(p.canProceed()).isTrue();
        assertThat(p.authorityBasis()).isEqualTo("direct report");
        assertThat(p.projectRole()).isEqualTo("Developer");
        assertThat(p.missingAccess()).extracting(pa -> pa.entitlement().entitlementCode()).containsExactlyInAnyOrder(
                "ATLAS_DEV", "AWS_ATLAS_DEV", "JIRA_USER", "ATLAS_DB_READ", "ATLAS_VAULT_READ", "CORP_VPN");

        AdditionResultDto r = addition.add("NT10020", "NT10036", projectId("ATLAS"), null);

        assertThat(r.addedBy()).isEqualTo("NT10020");
        assertThat(r.missingAccess()).hasSize(6);
        verify(iga, never()).createAccessRequest(any()); // membership only; nothing goes to the IGA

        assertThat(dashboard.getMyProjects("NT10036")).extracting(m -> m.project().projectName())
                .contains("Atlas Payments", "Novatech");
        // Now a member, so his manager can also remove him again
        assertThat(removal.preview("NT10020", "NT10036", projectId("ATLAS")).canProceed()).isTrue();
    }

    @Test
    void whoMayAddWhom() {
        assertThatThrownBy(() -> addition.preview("NT10036", "NT10042", projectId("ATLAS"), null))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("does not report to you");
        assertThatThrownBy(() -> addition.preview("NT10036", "NT10036", projectId("ATLAS"), null))
                .isInstanceOf(ForbiddenException.class); // employees ask for their own access instead
        assertThatThrownBy(() -> addition.preview("NT10051", "NT10036", projectId("ATLAS"), null))
                .isInstanceOf(ForbiddenException.class);
        // Grace is an admin, and also above Marco in the reporting line
        assertThat(addition.preview("NT10001", "NT10058", projectId("ORION"), null).canProceed()).isTrue();
    }

    @Test
    void blockersPreventTheAddition() {
        assertThat(addition.preview("NT10020", "NT10036", projectId("NOVATECH"), null).blockers())
                .singleElement().asString().contains("already a member");
        assertThat(addition.preview("NT10020", "NT10063", projectId("ZEPHYR"), null).blockers())
                .singleElement().asString().contains("not an active project");
        assertThat(addition.preview("NT10051", "NT10070", projectId("HELIOS"), null).blockers())
                .singleElement().asString().contains("not an active employee");
        assertThatThrownBy(() -> addition.add("NT10020", "NT10036", projectId("NOVATECH"), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void projectRoleCanBeGiven() {
        assertThat(addition.add("NT10020", "NT10058", projectId("ATLAS"), "Mobile SDK Consultant").projectRole())
                .isEqualTo("Mobile SDK Consultant");
    }

    @Test
    void removedMemberIsReactivatedNotDuplicated() {
        removal.remove("NT10020", "NT10042", projectId("NOVATECH"), "Moved to Helios");

        addition.add("NT10020", "NT10042", projectId("NOVATECH"), null);

        tx.executeWithoutResult(s -> {
            var rows = members.findAll().stream().filter(m -> m.getUser().getUserId().equals("NT10042")
                    && m.getProject().getProjectCode().equals("NOVATECH")).toList();
            assertThat(rows).singleElement().satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
                assertThat(m.getRemovedAt()).isNull();
                assertThat(m.getRemovalReason()).isNull();
            });
        });
    }

    @Test
    void addingWorksEvenWhenTheIgaIsDown() {
        when(iga.createAccessRequest(any())).thenThrow(new IgaIntegrationException("connection refused", null));

        addition.add("NT10020", "NT10036", projectId("ATLAS"), null);

        assertThat(dashboard.getMyProjects("NT10036")).extracting(m -> m.project().projectName())
                .contains("Atlas Payments");
    }

    private long projectId(String code) {
        return projects.findByProjectCodeIgnoreCase(code).orElseThrow().getId();
    }
}
