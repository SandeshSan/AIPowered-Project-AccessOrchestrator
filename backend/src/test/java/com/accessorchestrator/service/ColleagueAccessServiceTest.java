package com.accessorchestrator.service;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ColleagueComparisonDto;
import com.accessorchestrator.dto.ColleagueComparisonDtos.ComparedEntitlement;
import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectRepository;
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
import static org.mockito.Mockito.when;

/** Comparing access with a colleague, against the seeded demo data. */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ColleagueAccessServiceTest {

    @Autowired private ColleagueAccessService service;
    @Autowired private AccessRequestService requests;
    @Autowired private ProjectRepository projects;
    @Autowired private EntitlementRepository entitlements;
    @MockitoBean private IgaProvider iga;

    @BeforeEach
    void stubIga() {
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-40001", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void peersSeeTheDifferenceAndWhatTheirOwnProjectsRequire() {
        // John (Novatech) vs Asha (Novatech + Orion); Asha's expired DB read and revoked VPN do not count
        ColleagueComparisonDto c = service.compare("NT10036", "NT10042");

        assertThat(c.fullDetail()).isFalse();
        assertThat(codes(c.colleagueOnly())).containsExactly("ORION_DEV", "NOVATECH_JIRA");
        assertThat(item(c.colleagueOnly(), "NOVATECH_JIRA").requestableProjectName()).isEqualTo("Novatech");
        ComparedEntitlement orion = item(c.colleagueOnly(), "ORION_DEV");
        assertThat(orion.requestableProjectId()).isNull();
        assertThat(orion.note()).contains("ask your manager");
        assertThat(c.youOnly()).isEmpty();
        assertThat(c.inCommonCount()).isEqualTo(2); // NOVATECH_DEV, GCP_NOVATECH_DEV
    }

    @Test
    void aColleaguesHighRiskAccessIsRestrictedForPeers() {
        // Priya holds the high-risk Atlas Vault entitlement; John is not her manager
        ColleagueComparisonDto c = service.compare("NT10036", "NT10051");

        List<ComparedEntitlement> restricted = c.colleagueOnly().stream().filter(ComparedEntitlement::restricted).toList();
        assertThat(restricted).singleElement().satisfies(r -> {
            assertThat(r.application()).isEqualTo("HashiCorp Vault");
            assertThat(r.entitlementCode()).isNull();
            assertThat(r.entitlementName()).isNull();
            assertThat(r.entitlementId()).isNull();
        });
        assertThat(codes(c.colleagueOnly())).doesNotContain("ATLAS_VAULT_READ");
        assertThat(codes(c.youOnly())).containsExactlyInAnyOrder("NOVATECH_DEV", "GCP_NOVATECH_DEV");
    }

    @Test
    void managersAndAdminsSeeHighRiskAccessInFull() {
        ColleagueComparisonDto mei = service.compare("NT10020", "NT10051");   // Priya's manager
        ColleagueComparisonDto grace = service.compare("NT10001", "NT10051"); // admin

        assertThat(mei.fullDetail()).isTrue();
        assertThat(codes(mei.colleagueOnly())).contains("ATLAS_VAULT_READ");
        assertThat(mei.colleagueOnly()).noneMatch(ComparedEntitlement::restricted);
        assertThat(grace.fullDetail()).isTrue();
        assertThat(codes(grace.colleagueOnly())).contains("ATLAS_VAULT_READ");
    }

    @Test
    void somethingAlreadyRequestedIsNotOfferedAgain() {
        Long novatech = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        Long jira = entitlements.findByEntitlementCode("NOVATECH_JIRA").orElseThrow().getId();
        requests.requestAccess(new RequestAccessCommand("NT10036", novatech, List.of(jira), "test"));

        ComparedEntitlement item = item(service.compare("NT10036", "NT10042").colleagueOnly(), "NOVATECH_JIRA");

        assertThat(item.requestableProjectId()).isNull();
        assertThat(item.note()).contains("already have a request in progress").contains("REQ-40001");
    }

    @Test
    void comparingWithYourselfIsRefused() {
        assertThatThrownBy(() -> service.compare("NT10036", "nt10036")).isInstanceOf(BusinessRuleException.class);
    }

    private static List<String> codes(List<ComparedEntitlement> items) {
        return items.stream().map(ComparedEntitlement::entitlementCode).filter(java.util.Objects::nonNull).toList();
    }

    private static ComparedEntitlement item(List<ComparedEntitlement> items, String code) {
        return items.stream().filter(i -> code.equals(i.entitlementCode())).findFirst().orElseThrow();
    }
}
