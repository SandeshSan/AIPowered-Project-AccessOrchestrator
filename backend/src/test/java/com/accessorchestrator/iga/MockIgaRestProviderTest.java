package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contract between the backend adapter and the Mock IGA Service REST API. */
class MockIgaRestProviderTest {

    private MockRestServiceServer server;
    private MockIgaRestProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new MockIgaRestProvider(builder, "http://mock-iga");
    }

    @Test
    void createSendsEntitlementCodesAndMapsResponse() {
        server.expect(requestTo("http://mock-iga/mock-iga/access-requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"userId":"NT10036",
                         "entitlements":["NOVATECH_JIRA","NOVATECH_DB_READ","NOVATECH_VPN"],
                         "externalReference":"REQ-20260926-ABCD1234",
                         "requestedBy":"NT10036"}"""))
                .andRespond(withSuccess("{\"requestId\":\"REQ-10145\",\"status\":\"PENDING_APPROVAL\"}",
                        MediaType.APPLICATION_JSON));

        AccessRequestResponse response = provider.createAccessRequest(new IgaAccessRequest(
                "REQ-20260926-ABCD1234", com.accessorchestrator.domain.RequestType.GRANT, "NT10036", "NT10036", "john@novatech.example", "NOVATECH", "Joined",
                List.of(item("Jira", "NOVATECH_JIRA"), item("Database", "NOVATECH_DB_READ"),
                        item("VPN", "NOVATECH_VPN"))));

        assertThat(response.igaRequestId()).isEqualTo("REQ-10145");
        assertThat(response.status()).isEqualTo(RequestStatus.PENDING_APPROVAL);
        server.verify();
    }

    @Test
    void statusIsReadAndMapped() {
        server.expect(requestTo("http://mock-iga/mock-iga/access-requests/REQ-10145"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"requestId":"REQ-10145","status":"PROVISIONING","updatedAt":"2026-09-26T10:00:00Z",
                         "decisionComment":"ok","lifecycle":[],"history":[]}""", MediaType.APPLICATION_JSON));

        AccessRequestStatus status = provider.getRequestStatus("REQ-10145");

        assertThat(status.status()).isEqualTo(RequestStatus.PROVISIONING);
        assertThat(status.comment()).isEqualTo("ok");
    }

    @Test
    void serverErrorsBecomeIntegrationExceptions() {
        server.expect(requestTo("http://mock-iga/mock-iga/access-requests/REQ-1")).andRespond(withServerError());

        assertThatThrownBy(() -> provider.getRequestStatus("REQ-1")).isInstanceOf(IgaIntegrationException.class);
    }

    @Test
    void everyMockStatusMapsToADomainStatus() {
        assertThat(List.of("CREATED", "PENDING_APPROVAL", "APPROVED", "REJECTED", "PROVISIONING", "PROVISIONED")
                .stream().map(MockIgaRestProvider::toDomain))
                .containsExactly(RequestStatus.SUBMITTED, RequestStatus.PENDING_APPROVAL, RequestStatus.APPROVED,
                        RequestStatus.REJECTED, RequestStatus.PROVISIONING, RequestStatus.PROVISIONED);
        assertThatThrownBy(() -> MockIgaRestProvider.toDomain("WHATEVER"))
                .isInstanceOf(IgaIntegrationException.class);
    }

    private static IgaAccessRequest.Item item(String app, String code) {
        return new IgaAccessRequest.Item(app, code, "needed");
    }
}
