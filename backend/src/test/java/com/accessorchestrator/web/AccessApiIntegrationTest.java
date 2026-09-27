package com.accessorchestrator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API tests against the seeded Novatech scenario on in-memory H2. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
class AccessApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProjectRepository projectRepository;

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projectRepository.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
    }

    @Test
    void getUser() throws Exception {
        mvc.perform(get("/api/users/NT10036"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NT10036"))
                .andExpect(jsonPath("$.name").value("John"))
                .andExpect(jsonPath("$.role").value("Developer"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void unknownUserReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/api/users/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value(containsString("NOPE")));
    }

    @Test
    void listAndGetProjects() throws Exception {
        mvc.perform(adminGet("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].projectName", hasItem("Novatech")));

        mvc.perform(adminGet("/api/projects/{id}", novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCode").value("NOVATECH"));

        mvc.perform(adminGet("/api/projects/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectAccessProfile() throws Exception {
        mvc.perform(adminGet("/api/projects/{id}/access", novatechId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].entitlement.entitlementCode", hasItem("NOVATECH_VPN")))
                .andExpect(jsonPath("$[?(@.entitlement.entitlementCode == 'NOVATECH_WIKI')].required",
                        contains(false)));
    }

    @Test
    void userExistingAccess() throws Exception {
        mvc.perform(get("/api/users/NT10036/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].entitlement.entitlementCode",
                        contains("GCP_NOVATECH_DEV", "NOVATECH_DEV")));
    }

    @Test
    void analyzeReturnsDeterministicMissingAccess() throws Exception {
        mvc.perform(post("/api/access/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NT10036"))
                .andExpect(jsonPath("$.project").value("Novatech"))
                .andExpect(jsonPath("$.requiredAccess", hasSize(5)))
                .andExpect(jsonPath("$.existingAccess", hasSize(2)))
                .andExpect(jsonPath("$.missingAccess[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN")));
    }

    @Test
    void analyzeRejectsInvalidBody() throws Exception {
        mvc.perform(post("/api/access/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"projectId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists())
                .andExpect(jsonPath("$.errors.projectId").exists());
    }

    @Test
    void analyzeUnknownProjectReturns404() throws Exception {
        mvc.perform(post("/api/access/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"NT10036\",\"projectId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDraftRequestForMissingAccessAndReadItBack() throws Exception {
        MvcResult result = mvc.perform(post("/api/access/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdBy").value("NT10036"))
                .andExpect(jsonPath("$.items[*].entitlement.entitlementCode",
                        contains("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void createRequestForSubsetOfMissingAccess() throws Exception {
        mvc.perform(post("/api/access/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"NT10036\",\"projectId\":" + novatechId
                                + ",\"entitlementCodes\":[\"NOVATECH_VPN\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[*].entitlement.entitlementCode", contains("NOVATECH_VPN")));
    }

    @Test
    void cannotRequestAccessThatIsNotMissing() throws Exception {
        mvc.perform(post("/api/access/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"NT10036\",\"projectId\":" + novatechId
                                + ",\"entitlementCodes\":[\"NOVATECH_DEV\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("NOVATECH_DEV")));
    }

    @Test
    void agentWithoutConfiguredModelReturns503() throws Exception {
        mvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"I joined Novatech\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI agent not configured"));
    }

    @Test
    void agentRejectsEmptyMessage() throws Exception {
        mvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRequestReturns404() throws Exception {
        mvc.perform(get("/api/access/requests/REQ-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    /** The project catalog is admin-only; NT10001 (Grace) is the seeded admin. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(
            String url, Object... vars) {
        return get(url, vars).with(user("NT10001"));
    }
}
