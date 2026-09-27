package com.mockiga.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test profile: auto-provision off, provisioning takes 200 ms. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockIgaApiTest {

    private static final String BASE = "/mock-iga/access-requests";
    private static final String NOVATECH_REQUEST = """
            {"userId":"NT10036","entitlements":["NOVATECH_JIRA","NOVATECH_DB_READ","NOVATECH_VPN"]}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void createReturnsRequestIdAndPendingApproval() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(NOVATECH_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/mock-iga/access-requests/REQ-\\d+")))
                .andExpect(jsonPath("$.requestId").value(matchesPattern("REQ-\\d{5,}")))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void getReturnsDetailWithLifecycle() throws Exception {
        String id = create();
        mvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NT10036"))
                .andExpect(jsonPath("$.requestedBy").value("NT10036"))
                .andExpect(jsonPath("$.entitlements", contains("NOVATECH_JIRA", "NOVATECH_DB_READ", "NOVATECH_VPN")))
                .andExpect(jsonPath("$.statusLabel").value("Pending Approval"))
                .andExpect(jsonPath("$.lifecycle[*].label", contains("Request Created", "Pending Approval",
                        "Approved", "Provisioning", "Provisioned")))
                .andExpect(jsonPath("$.lifecycle[*].state", contains("COMPLETED", "CURRENT", "UPCOMING",
                        "UPCOMING", "UPCOMING")));
    }

    @Test
    void approveThenProvisionReachesProvisioned() throws Exception {
        String id = create();

        mvc.perform(post(BASE + "/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"jane.manager\",\"comment\":\"Approved for Novatech\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("jane.manager"));

        mvc.perform(post(BASE + "/" + id + "/provision"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVISIONING"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get(BASE + "/" + id))
                        .andExpect(jsonPath("$.status").value("PROVISIONED"))
                        .andExpect(jsonPath("$.lifecycle[*].state", contains("COMPLETED", "COMPLETED",
                                "COMPLETED", "COMPLETED", "COMPLETED")))
                        .andExpect(jsonPath("$.history[*].status", contains("CREATED", "PENDING_APPROVAL",
                                "APPROVED", "PROVISIONING", "PROVISIONED"))));
    }

    @Test
    void rejectIsTerminal() throws Exception {
        String id = create();

        mvc.perform(post(BASE + "/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Not on the project roster\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedBy").value("manager"))
                .andExpect(jsonPath("$.lifecycle[*].label", contains("Request Created", "Pending Approval",
                        "Rejected")));

        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().isConflict());
        mvc.perform(post(BASE + "/" + id + "/provision")).andExpect(status().isConflict());
    }

    @Test
    void invalidTransitionsReturn409() throws Exception {
        String id = create();
        mvc.perform(post(BASE + "/" + id + "/provision"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid state transition"));

        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().isConflict());
    }

    @Test
    void unknownRequestReturns404() throws Exception {
        mvc.perform(get(BASE + "/REQ-0")).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/REQ-0/approve")).andExpect(status().isNotFound());
    }

    @Test
    void createValidatesBody() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"\",\"entitlements\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists())
                .andExpect(jsonPath("$.errors.entitlements").exists());
    }

    @Test
    void duplicateEntitlementsAreCollapsed() throws Exception {
        String body = "{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_VPN\",\"NOVATECH_VPN\"]}";
        String id = JsonPath.read(mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString(), "$.requestId");

        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.entitlements", contains("NOVATECH_VPN")));
    }

    @Test
    void listCanFilterByStatus() throws Exception {
        String pending = create();
        String approved = create();
        mvc.perform(post(BASE + "/" + approved + "/approve")).andExpect(status().isOk());

        mvc.perform(get(BASE).param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].requestId", hasItem(approved)))
                .andExpect(jsonPath("$[?(@.requestId == '" + pending + "')]").isEmpty());
    }

    private String create() throws Exception {
        String json = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(NOVATECH_REQUEST))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.requestId");
    }
}
