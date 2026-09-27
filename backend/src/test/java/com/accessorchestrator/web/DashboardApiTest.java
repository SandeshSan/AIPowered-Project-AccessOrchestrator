package com.accessorchestrator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void meDefaultsToDemoUserAndHonoursHeader() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("NT10036"));
        mvc.perform(get("/api/me").with(user("NT10042")))
                .andExpect(jsonPath("$.name").value("Asha"));
        mvc.perform(get("/api/me").with(user("NOPE"))).andExpect(status().isNotFound());
    }

    @Test
    void listUsers() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", containsInAnyOrder("NT10001", "NT10020", "NT10036", "NT10042", "NT10051", "NT10058", "NT10063", "NT10070")));
    }

    @Test
    void capabilitiesDescribeAdminAndReports() throws Exception {
        mvc.perform(get("/api/me/capabilities").with(user("NT10020")))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.reports[*].user.userId", containsInAnyOrder(
                        "NT10036", "NT10042", "NT10051", "NT10058", "NT10063", "NT10070")))
                .andExpect(jsonPath("$.reports[?(@.user.userId == 'NT10070')].level", contains(2)))
                .andExpect(jsonPath("$.reports[?(@.user.userId == 'NT10036')].projects[*].projectName",
                        contains("Novatech")));
        mvc.perform(get("/api/me/capabilities").with(user("NT10036")))
                .andExpect(jsonPath("$.reports", hasSize(0)));
        mvc.perform(get("/api/users/NT10070").with(user("NT10070")))
                .andExpect(jsonPath("$.managerId").value("NT10051"))
                .andExpect(jsonPath("$.managerName").value("Priya"));
        mvc.perform(get("/api/me/capabilities").with(user("NT10001")))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.reports[*].user.userId", hasItem("NT10020")));
    }

    @Test
    void teamShowsReporteesWithTheirAccess() throws Exception {
        mvc.perform(get("/api/team").with(user("NT10020")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[?(@.user.userId == 'NT10042')].activeAccess[*].entitlement.entitlementCode",
                        hasItem("NOVATECH_DEV")))
                .andExpect(jsonPath("$[?(@.user.userId == 'NT10070')].level", contains(2)));
        mvc.perform(get("/api/team").with(user("NT10051")))
                .andExpect(jsonPath("$[*].user.userId", contains("NT10070")));
        mvc.perform(get("/api/team").with(user("NT10036")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void myProjectsIncludeCoverage() throws Exception {
        mvc.perform(get("/api/users/NT10042/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].project.projectName", contains("Novatech", "Orion")))
                .andExpect(jsonPath("$[0].projectRole").value("QA Engineer"))
                .andExpect(jsonPath("$[0].access.missingCount").value(2))
                .andExpect(jsonPath("$[1].access.missingCount").value(1));
    }

    @Test
    void dashboardForJohn() throws Exception {
        mvc.perform(get("/api/users/NT10036/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("John"))
                .andExpect(jsonPath("$.activeAccessCount").value(2))
                .andExpect(jsonPath("$.pendingRequestCount").value(0))
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.missingAccessCount").value(3))
                .andExpect(jsonPath("$.missingAlreadyRequestedCount").value(0))
                .andExpect(jsonPath("$.projects[0].access.coveragePercent").value(40))
                .andExpect(jsonPath("$.activeAccess", hasSize(2)))
                .andExpect(jsonPath("$.recentlyProvisioned", hasSize(2)));
    }

    @Test
    void dashboardSumsMissingAcrossProjects() throws Exception {
        mvc.perform(get("/api/users/NT10042/dashboard"))
                .andExpect(jsonPath("$.activeAccessCount").value(4))
                .andExpect(jsonPath("$.projectCount").value(2))
                .andExpect(jsonPath("$.missingAccessCount").value(3));
    }
}
