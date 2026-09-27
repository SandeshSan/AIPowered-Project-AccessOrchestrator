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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevocationAndCancelApiTest {

    private static final String BASE = "/mock-iga/access-requests";

    @Autowired
    private MockMvc mvc;

    @Test
    void revocationNeedsApprovalThenRevokes() throws Exception {
        String id = create("""
                {"type":"REVOKE","userId":"NT10042","entitlements":["NOVATECH_DEV"],
                 "requestedBy":"NT10020","justification":"Moved to Helios"}""");

        mvc.perform(get(BASE + "/" + id))
                .andExpect(jsonPath("$.type").value("REVOKE"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.justification").value("Moved to Helios"))
                .andExpect(jsonPath("$.lifecycle[*].label", contains("Revocation Requested", "Pending Approval",
                        "Approved", "Revoking", "Revoked")));

        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/provision"))
                .andExpect(jsonPath("$.statusLabel").value("Revoking"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> mvc.perform(get(BASE + "/" + id))
                .andExpect(jsonPath("$.status").value("PROVISIONED"))
                .andExpect(jsonPath("$.statusLabel").value("Revoked")));
    }

    @Test
    void grantIsTheDefaultType() throws Exception {
        String id = create("{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_VPN\"]}");
        mvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.type").value("GRANT"));
    }

    @Test
    void pendingOrApprovedRequestsCanBeCancelled() throws Exception {
        String pending = create("{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_VPN\"]}");
        mvc.perform(post(BASE + "/" + pending + "/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"orchestrator\",\"comment\":\"Removed from project\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.lifecycle[*].label", contains("Request Created", "Pending Approval", "Cancelled")))
                .andExpect(jsonPath("$.lifecycle[2].state").value("CANCELLED"));

        String approved = create("{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_JIRA\"]}");
        mvc.perform(post(BASE + "/" + approved + "/approve")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + approved + "/cancel"))
                .andExpect(jsonPath("$.lifecycle[*].label",
                        contains("Request Created", "Pending Approval", "Approved", "Cancelled")));
    }

    @Test
    void provisioningOrFinishedRequestsCannotBeCancelled() throws Exception {
        String id = create("{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_VPN\"]}");
        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/provision")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/cancel")).andExpect(status().isConflict());

        String cancelled = create("{\"userId\":\"NT10036\",\"entitlements\":[\"NOVATECH_JIRA\"]}");
        mvc.perform(post(BASE + "/" + cancelled + "/cancel")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + cancelled + "/approve")).andExpect(status().isConflict());
    }

    private String create(String body) throws Exception {
        String json = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.requestId");
    }
}
