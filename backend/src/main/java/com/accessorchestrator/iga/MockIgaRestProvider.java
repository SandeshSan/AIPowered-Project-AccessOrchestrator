package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;

/**
 * {@link IgaProvider} backed by the Mock IGA Service's REST API. A real Saviynt adapter would implement the
 * same interface with Saviynt's endpoints and auth, leaving the rest of the application untouched.
 */
@Component
@ConditionalOnProperty(name = "app.iga.provider", havingValue = "mock-rest")
public class MockIgaRestProvider implements IgaProvider {

    private static final String REQUESTS = "/mock-iga/access-requests";

    private final RestClient client;

    public MockIgaRestProvider(RestClient.Builder builder, @Value("${app.iga.mock.base-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public String name() {
        return "mock-rest";
    }

    @Override
    public AccessRequestResponse createAccessRequest(IgaAccessRequest request) {
        MockCreateRequest body = new MockCreateRequest(request.type().name(), request.userId(),
                request.items().stream().map(IgaAccessRequest.Item::entitlementCode).toList(),
                request.requestId(), request.requestedBy(), request.justification());
        try {
            MockCreateResponse response = client.post().uri(REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MockCreateResponse.class);
            if (response == null || response.requestId() == null) {
                throw new IgaIntegrationException("Mock IGA returned an empty response", null);
            }
            return new AccessRequestResponse(response.requestId(), toDomain(response.status()),
                    "Submitted to Mock IGA as " + response.requestId());
        } catch (RestClientException e) {
            throw new IgaIntegrationException("Could not submit request to Mock IGA: " + e.getMessage(), e);
        }
    }

    @Override
    public AccessRequestStatus getRequestStatus(String igaRequestId) {
        try {
            MockRequestView view = client.get().uri(REQUESTS + "/{id}", igaRequestId)
                    .retrieve()
                    .body(MockRequestView.class);
            if (view == null) {
                throw new IgaIntegrationException("Mock IGA returned an empty response", null);
            }
            return new AccessRequestStatus(view.requestId(), toDomain(view.status()), view.updatedAt(),
                    view.decisionComment());
        } catch (RestClientException e) {
            throw new IgaIntegrationException("Could not read status of " + igaRequestId + " from Mock IGA: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public AccessRequestStatus cancelRequest(String igaRequestId, String actor, String comment) {
        try {
            MockRequestView view = client.post().uri(REQUESTS + "/{id}/cancel", igaRequestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MockActionRequest(actor, comment))
                    .retrieve()
                    .body(MockRequestView.class);
            if (view == null) {
                throw new IgaIntegrationException("Mock IGA returned an empty response", null);
            }
            return new AccessRequestStatus(view.requestId(), toDomain(view.status()), view.updatedAt(),
                    view.decisionComment());
        } catch (RestClientException e) {
            throw new IgaIntegrationException("Could not cancel " + igaRequestId + " at Mock IGA: " + e.getMessage(), e);
        }
    }

    /** Anti-corruption mapping from the IGA's vocabulary to ours. */
    static RequestStatus toDomain(String igaStatus) {
        return switch (igaStatus) {
            case "CREATED" -> RequestStatus.SUBMITTED;
            case "PENDING_APPROVAL" -> RequestStatus.PENDING_APPROVAL;
            case "APPROVED" -> RequestStatus.APPROVED;
            case "REJECTED" -> RequestStatus.REJECTED;
            case "PROVISIONING" -> RequestStatus.PROVISIONING;
            case "PROVISIONED" -> RequestStatus.PROVISIONED;
            case "CANCELLED" -> RequestStatus.CANCELLED;
            case null, default -> throw new IgaIntegrationException("Unknown Mock IGA status: " + igaStatus, null);
        };
    }

    record MockCreateRequest(String type, String userId, List<String> entitlements, String externalReference,
                             String requestedBy, String justification) {
    }

    record MockActionRequest(String actor, String comment) {
    }

    record MockCreateResponse(String requestId, String status) {
    }

    record MockRequestView(String requestId, String status, Instant updatedAt, String decisionComment) {
    }
}
