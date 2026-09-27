package com.accessorchestrator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.domain.Project;
import com.accessorchestrator.domain.User;
import com.accessorchestrator.repository.ProjectRepository;
import com.accessorchestrator.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 2 – Access intelligence: required / existing / missing retrieval and the comparison API. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
class AccessIntelligenceApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projectRepository.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
    }

    // --- 1. Required access retrieval ----------------------------------------------------------------

    @Test
    void requiredAccessExcludesOptionalEntries() throws Exception {
        mvc.perform(adminGet("/api/projects/{id}/required-access", novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entitlement.entitlementCode", contains(
                        "NOVATECH_DB_READ", "GCP_NOVATECH_DEV", "NOVATECH_DEV", "NOVATECH_JIRA", "NOVATECH_VPN")))
                .andExpect(jsonPath("$[*].required", not(hasItem(false))));
    }

    @Test
    void requiredAccessForUnknownProjectIs404() throws Exception {
        mvc.perform(adminGet("/api/projects/{id}/required-access", 999_999)).andExpect(status().isNotFound());
    }

    // --- 2. Existing access retrieval ----------------------------------------------------------------

    @Test
    void existingAccessIgnoresRevokedAndExpired() throws Exception {
        mvc.perform(get("/api/users/NT10042/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entitlement.entitlementCode",
                        contains("GCP_NOVATECH_DEV", "NOVATECH_DEV", "ORION_DEV", "NOVATECH_JIRA")))
                .andExpect(jsonPath("$[*].status", not(hasItem("REVOKED"))))
                .andExpect(jsonPath("$[*].status", not(hasItem("EXPIRED"))));
    }

    // --- 3. Missing access calculation ---------------------------------------------------------------

    @Test
    void missingAccessEndpoint() throws Exception {
        mvc.perform(get("/api/access/missing").param("userId", "NT10036").param("projectId", "" + novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN")));
    }

    @Test
    void revokedAndExpiredAccessCountAsMissing() throws Exception {
        mvc.perform(get("/api/access/missing").param("userId", "NT10042").param("projectId", "" + novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_VPN")));
    }

    @Test
    void missingAccessValidatesParameters() throws Exception {
        mvc.perform(get("/api/access/missing").param("userId", "NT10036"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/access/missing").param("userId", " ").param("projectId", "" + novatechId))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/access/missing").param("userId", "NT10036").param("projectId", "abc"))
                .andExpect(status().isBadRequest());
    }

    // --- 4. Access comparison API --------------------------------------------------------------------

    @Test
    void compareJohnOnNovatech() throws Exception {
        mvc.perform(compare("NT10036", novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("John"))
                .andExpect(jsonPath("$.project").value("Novatech"))
                .andExpect(jsonPath("$.summary.requiredCount").value(5))
                .andExpect(jsonPath("$.summary.grantedCount").value(2))
                .andExpect(jsonPath("$.summary.missingCount").value(3))
                .andExpect(jsonPath("$.summary.additionalCount").value(0))
                .andExpect(jsonPath("$.summary.coveragePercent").value(40))
                .andExpect(jsonPath("$.summary.fullyProvisioned").value(false))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[?(@.status == 'GRANTED')].entitlement.entitlementCode",
                        contains("GCP_NOVATECH_DEV", "NOVATECH_DEV")))
                .andExpect(jsonPath("$.items[?(@.status == 'MISSING')].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN")))
                .andExpect(jsonPath("$.missingAccess[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN")))
                .andExpect(jsonPath("$.additionalAccess", empty()))
                .andExpect(jsonPath("$.verification.rule").value("Required - Existing = Missing"))
                .andExpect(jsonPath("$.verification.expression")
                        .value("5 required - 2 already granted = 3 missing"))
                .andExpect(jsonPath("$.verification.verified").value(true));
    }

    @Test
    void compareAshaShowsAdditionalAccess() throws Exception {
        mvc.perform(compare("NT10042", novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.coveragePercent").value(60))
                .andExpect(jsonPath("$.missingAccess[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_VPN")))
                .andExpect(jsonPath("$.additionalAccess[*].entitlement.entitlementCode", contains("ORION_DEV")))
                .andExpect(jsonPath("$.verification.expression")
                        .value("5 required - 3 already granted = 2 missing"));
    }

    @Test
    void compareValidatesAndReportsNotFound() throws Exception {
        mvc.perform(post("/api/access/compare").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists())
                .andExpect(jsonPath("$.errors.projectId").exists());
        mvc.perform(compare("NOPE", novatechId)).andExpect(status().isNotFound());
    }

    // --- Verification: Required - Existing = Missing, for every seeded user x project ----------------

    /**
     * Pulls required and existing access from their own endpoints, computes the set difference here in the
     * test, and checks that /missing, /analyze and /compare all agree with it.
     */
    @Test
    void requiredMinusExistingEqualsMissingForAllSeededUsersAndProjects() throws Exception {
        List<User> users = userRepository.findAll();
        List<Project> projects = projectRepository.findAll();
        assertThat(users).hasSizeGreaterThanOrEqualTo(2);
        assertThat(projects).hasSizeGreaterThanOrEqualTo(2);

        for (User user : users) {
            Set<String> existing = codes(getJson("/api/users/" + user.getUserId() + "/access"),
                    "$[*].entitlement.entitlementCode");
            for (Project project : projects) {
                String scenario = user.getUserId() + " @ " + project.getProjectCode();
                Set<String> required = codes(getJson("/api/projects/" + project.getId() + "/required-access"),
                        "$[*].entitlement.entitlementCode");

                Set<String> expectedMissing = new HashSet<>(required);
                expectedMissing.removeAll(existing);

                Set<String> fromMissing = codes(getJson("/api/access/missing?userId=" + user.getUserId()
                        + "&projectId=" + project.getId()), "$[*].entitlement.entitlementCode");
                String analyze = postJson("/api/access/analyze", user.getUserId(), project.getId());
                String comparison = postJson("/api/access/compare", user.getUserId(), project.getId());

                assertThat(fromMissing).as(scenario + " /missing").isEqualTo(expectedMissing);
                assertThat(codes(analyze, "$.missingAccess[*].entitlement.entitlementCode"))
                        .as(scenario + " /analyze").isEqualTo(expectedMissing);
                assertThat(codes(comparison, "$.missingAccess[*].entitlement.entitlementCode"))
                        .as(scenario + " /compare").isEqualTo(expectedMissing);
                assertThat(codes(comparison, "$.items[?(@.status == 'MISSING')].entitlement.entitlementCode"))
                        .as(scenario + " /compare items").isEqualTo(expectedMissing);
                assertThat((Boolean) JsonPath.read(comparison, "$.verification.verified"))
                        .as(scenario + " verified").isTrue();
                assertThat((Integer) JsonPath.read(comparison, "$.summary.missingCount"))
                        .as(scenario + " count").isEqualTo(expectedMissing.size());
            }
        }
    }

    private MockHttpServletRequestBuilder compare(String userId, long projectId) {
        return post("/api/access/compare").contentType(MediaType.APPLICATION_JSON)
                .content(body(userId, projectId));
    }

    private String getJson(String url) throws Exception {
        return mvc.perform(get(url).with(user("NT10001"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String postJson(String url, String userId, long projectId) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body(userId, projectId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String body(String userId, long projectId) {
        return "{\"userId\":\"" + userId + "\",\"projectId\":" + projectId + "}";
    }

    private static Set<String> codes(String json, String path) {
        List<String> list = JsonPath.read(json, path);
        return new HashSet<>(list);
    }

    /** The project catalog is admin-only; NT10001 (Grace) is the seeded admin. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(
            String url, Object... vars) {
        return get(url, vars).with(user("NT10001"));
    }
}
