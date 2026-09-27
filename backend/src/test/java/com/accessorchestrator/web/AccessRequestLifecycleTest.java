package com.accessorchestrator.web;

import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.AccessRequestStatus;
import com.accessorchestrator.iga.IgaAccessRequest;
import com.accessorchestrator.iga.IgaIntegrationException;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Draft -> submit -> IGA approval -> provisioning -> access recorded, with the IGA replaced by a Mockito
 * stub so each IGA status can be driven explicitly. This context mutates seeded access, so it is discarded
 * afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccessRequestLifecycleTest {

    private static final String IGA_ID = "REQ-10145";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProjectRepository projectRepository;

    @MockitoBean
    private IgaProvider igaProvider;

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projectRepository.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        when(igaProvider.name()).thenReturn("stub");
        when(igaProvider.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse(IGA_ID, RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void fullLifecycleGrantsAccessOnlyAfterProvisioning() throws Exception {
        // Asha: DB_READ expired and VPN revoked -> both missing; provisioning must reactivate those rows
        String id = createDraft("NT10042");

        mvc.perform(get("/api/access/requests/" + id))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lifecycle[*].state",
                        contains("CURRENT", "UPCOMING", "UPCOMING", "UPCOMING", "UPCOMING")));

        mvc.perform(post("/api/access/requests/" + id + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.igaRequestId").value(IGA_ID))
                .andExpect(jsonPath("$.lifecycle[*].state",
                        contains("COMPLETED", "CURRENT", "UPCOMING", "UPCOMING", "UPCOMING")));

        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(igaProvider).createAccessRequest(sent.capture());
        assertThat(sent.getValue().userId()).isEqualTo("NT10042");
        assertThat(sent.getValue().items()).extracting(IgaAccessRequest.Item::entitlementCode)
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_VPN");

        igaReports(RequestStatus.APPROVED);
        sync(id).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.items[*].status", contains("APPROVED", "APPROVED")));
        assertStillMissing("NT10042", "NOVATECH_DB_READ", "NOVATECH_VPN");

        igaReports(RequestStatus.PROVISIONING);
        sync(id).andExpect(jsonPath("$.status").value("PROVISIONING"));
        assertStillMissing("NT10042", "NOVATECH_DB_READ", "NOVATECH_VPN");

        igaReports(RequestStatus.PROVISIONED);
        sync(id).andExpect(jsonPath("$.status").value("PROVISIONED"))
                .andExpect(jsonPath("$.items[*].status", contains("PROVISIONED", "PROVISIONED")))
                .andExpect(jsonPath("$.lifecycle[*].state",
                        contains("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED")));

        mvc.perform(get("/api/users/NT10042/access"))
                .andExpect(jsonPath("$[*].entitlement.entitlementCode", hasItems("NOVATECH_DB_READ", "NOVATECH_VPN")));
        compare("NT10042")
                .andExpect(jsonPath("$.summary.fullyProvisioned").value(true))
                .andExpect(jsonPath("$.verification.expression").value("5 required - 5 already granted = 0 missing"));
    }

    @Test
    void rejectionGrantsNothing() throws Exception {
        String id = createDraft("NT10036");
        mvc.perform(post("/api/access/requests/" + id + "/submit")).andExpect(status().isOk());

        igaReports(RequestStatus.REJECTED);
        sync(id).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.items[*].status", contains("REJECTED", "REJECTED", "REJECTED")))
                .andExpect(jsonPath("$.lifecycle[*].label", contains("Request Created", "Pending Approval", "Rejected")));

        assertStillMissing("NT10036", "NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void terminalRequestsAreNotSyncedAgain() throws Exception {
        String id = createDraft("NT10036");
        mvc.perform(post("/api/access/requests/" + id + "/submit")).andExpect(status().isOk());
        igaReports(RequestStatus.REJECTED);
        sync(id);

        igaReports(RequestStatus.PROVISIONED); // a misbehaving IGA must not flip a rejected request
        sync(id).andExpect(jsonPath("$.status").value("REJECTED"));
        assertStillMissing("NT10036", "NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void cannotSubmitTwiceOrSyncBeforeSubmitting() throws Exception {
        String id = createDraft("NT10036");
        mvc.perform(post("/api/access/requests/" + id + "/sync")).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/access/requests/" + id + "/submit")).andExpect(status().isOk());
        mvc.perform(post("/api/access/requests/" + id + "/submit")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void igaOutageReturns502AndLeavesRequestInDraft() throws Exception {
        when(igaProvider.createAccessRequest(any())).thenThrow(new IgaIntegrationException("connection refused", null));
        String id = createDraft("NT10036");

        mvc.perform(post("/api/access/requests/" + id + "/submit"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("IGA unavailable"));
        mvc.perform(get("/api/access/requests/" + id)).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftIsNotSubmittedWhenAccessWasGrantedMeanwhile() throws Exception {
        String first = createDraft("NT10042");
        String second = createDraft("NT10042");

        mvc.perform(post("/api/access/requests/" + first + "/submit")).andExpect(status().isOk());
        igaReports(RequestStatus.PROVISIONED);
        sync(first);

        mvc.perform(post("/api/access/requests/" + second + "/submit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already holds")));
        verify(igaProvider, org.mockito.Mockito.times(1)).createAccessRequest(any());
    }

    @Test
    void listRequestsForUser() throws Exception {
        String id = createDraft("NT10036");
        mvc.perform(get("/api/access/requests").param("userId", "NT10036"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].requestId", hasItems(id)));
        verify(igaProvider, never()).createAccessRequest(any());
    }

    private String createDraft(String userId) throws Exception {
        String json = mvc.perform(post("/api/access/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"projectId\":" + novatechId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.requestId");
    }

    private void igaReports(RequestStatus status) {
        when(igaProvider.getRequestStatus(IGA_ID))
                .thenReturn(new AccessRequestStatus(IGA_ID, status, Instant.now(), null));
    }

    private ResultActions sync(String id) throws Exception {
        return mvc.perform(post("/api/access/requests/" + id + "/sync")).andExpect(status().isOk());
    }

    private ResultActions compare(String userId) throws Exception {
        return mvc.perform(post("/api/access/compare").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"projectId\":" + novatechId + "}"))
                .andExpect(status().isOk());
    }

    private void assertStillMissing(String userId, String... codes) throws Exception {
        compare(userId).andExpect(jsonPath("$.missingAccess[*].entitlement.entitlementCode", contains(codes)));
        mvc.perform(get("/api/users/" + userId + "/access"))
                .andExpect(jsonPath("$[*].entitlement.entitlementCode", not(hasItems(codes))));
    }
}
