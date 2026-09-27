package com.accessorchestrator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.dto.RequestAccessCommand;
import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectRepository;
import com.accessorchestrator.service.AccessRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pins the extended demo scenarios so the demo data keeps telling the intended stories. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
class SeedScenariosTest {

    @Autowired private MockMvc mvc;
    @Autowired private ProjectRepository projects;
    @Autowired private EntitlementRepository entitlements;
    @Autowired private AccessRequestService accessRequestService;

    @Test
    void priyaIsFullyProvisionedOnAtlasAndPartialOnHelios() throws Exception {
        compare("NT10051", "ATLAS")
                .andExpect(jsonPath("$.summary.fullyProvisioned").value(true))
                .andExpect(jsonPath("$.summary.requiredCount").value(6))
                .andExpect(jsonPath("$.additionalAccess[*].entitlement.entitlementCode", hasItem("ATLAS_DATADOG")));
        compare("NT10051", "HELIOS")
                .andExpect(jsonPath("$.missingAccess[*].entitlement.entitlementCode",
                        contains("HELIOS_APP_STORE", "HELIOS_FIGMA", "HELIOS_FIREBASE")));
        mvc.perform(get("/api/access/requests").param("userId", "NT10051"))
                .andExpect(jsonPath("$[0].igaRequestId").value("REQ-09870"))
                .andExpect(jsonPath("$[0].status").value("PROVISIONED"));
    }

    @Test
    void marcoHasNothingOnHeliosAndARejectedRequest() throws Exception {
        compare("NT10058", "HELIOS")
                .andExpect(jsonPath("$.summary.coveragePercent").value(0))
                .andExpect(jsonPath("$.summary.missingCount").value(6));
        mvc.perform(get("/api/access/requests").param("userId", "NT10058"))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].items[*].entitlement.entitlementCode", contains("HELIOS_APP_STORE")))
                .andExpect(jsonPath("$[0].lifecycle[*].label",
                        contains("Request Created", "Pending Approval", "Rejected")));
    }

    @Test
    void corporateVpnIsSharedByAtlasAndHelios() throws Exception {
        for (String code : List.of("ATLAS", "HELIOS")) {
            mvc.perform(adminGet("/api/projects/{id}/required-access", projectId(code)))
                    .andExpect(jsonPath("$[*].entitlement.entitlementCode", hasItem("CORP_VPN")));
        }
        // Priya's single CORP_VPN grant covers both projects
        compare("NT10051", "HELIOS")
                .andExpect(jsonPath("$.items[?(@.entitlement.entitlementCode == 'CORP_VPN')].status",
                        contains("GRANTED")));
    }

    @Test
    void elenaIsFullyProvisionedOnOrionAndHerRevokedAccessDoesNotCount() throws Exception {
        compare("NT10063", "ORION").andExpect(jsonPath("$.summary.fullyProvisioned").value(true));
        mvc.perform(get("/api/users/NT10063/access"))
                .andExpect(jsonPath("$[*].entitlement.entitlementCode",
                        containsInAnyOrder("ORION_DEV", "GCP_ORION_BQ_READ")));
    }

    @Test
    void closedProjectIsListedButCannotBeRequested() throws Exception {
        mvc.perform(adminGet("/api/projects/{id}", projectId("ZEPHYR"))).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThatThrownBy(() -> accessRequestService.requestAccess(new RequestAccessCommand("NT10063",
                projectId("ZEPHYR"), List.of(entitlementId("ZEPHYR_DEV")), "test")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not active");
    }

    @Test
    void inactiveUserCannotRequestAccess() throws Exception {
        mvc.perform(get("/api/users/NT10070")).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(get("/api/users/NT10070/access")).andExpect(jsonPath("$", empty()));
        assertThatThrownBy(() -> accessRequestService.requestAccess(new RequestAccessCommand("NT10070",
                projectId("ATLAS"), List.of(entitlementId("ATLAS_DEV")), "test")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not active");
    }

    private ResultActions compare(String userId, String projectCode) throws Exception {
        return mvc.perform(post("/api/access/compare").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"projectId\":" + projectId(projectCode) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification.verified").value(true));
    }

    private long projectId(String code) {
        return projects.findByProjectCodeIgnoreCase(code).orElseThrow().getId();
    }

    private long entitlementId(String code) {
        return entitlements.findByEntitlementCode(code).orElseThrow().getId();
    }

    /** The project catalog is admin-only; NT10001 (Grace) is the seeded admin. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(
            String url, Object... vars) {
        return get(url, vars).with(user("NT10001"));
    }
}
