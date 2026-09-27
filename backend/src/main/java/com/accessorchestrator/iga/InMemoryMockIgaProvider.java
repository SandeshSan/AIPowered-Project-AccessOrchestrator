package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.exception.ResourceNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process stand-in used by tests (app.iga.provider=in-memory): accepts requests into PENDING_APPROVAL
 * and never advances them. The demo runs against the Mock IGA Service via {@link MockIgaRestProvider}.
 */
@Component
@ConditionalOnProperty(name = "app.iga.provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryMockIgaProvider implements IgaProvider {

    private final AtomicLong sequence = new AtomicLong(1000);
    private final Map<String, AccessRequestStatus> requests = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "in-memory";
    }

    @Override
    public AccessRequestResponse createAccessRequest(IgaAccessRequest request) {
        String igaRequestId = "IGA-" + sequence.incrementAndGet();
        requests.put(igaRequestId, new AccessRequestStatus(igaRequestId, RequestStatus.PENDING_APPROVAL,
                Instant.now(), "Awaiting manager approval"));
        return new AccessRequestResponse(igaRequestId, RequestStatus.PENDING_APPROVAL,
                "Request " + request.requestId() + " accepted with " + request.items().size() + " item(s)");
    }

    @Override
    public AccessRequestStatus getRequestStatus(String igaRequestId) {
        AccessRequestStatus status = requests.get(igaRequestId);
        if (status == null) {
            throw new ResourceNotFoundException("IGA request", igaRequestId);
        }
        return status;
    }

    @Override
    public AccessRequestStatus cancelRequest(String igaRequestId, String actor, String comment) {
        getRequestStatus(igaRequestId);
        AccessRequestStatus cancelled = new AccessRequestStatus(igaRequestId, RequestStatus.CANCELLED, Instant.now(),
                comment);
        requests.put(igaRequestId, cancelled);
        return cancelled;
    }
}
