package com.mockiga.service;

import com.mockiga.config.MockIgaProperties;
import com.mockiga.domain.IgaRequest;
import com.mockiga.domain.IgaRequestSnapshot;
import com.mockiga.domain.IgaRequestStatus;
import com.mockiga.domain.IgaRequestType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory request store and approval workflow. Data is lost on restart, which is fine for a mock. */
@Service
public class IgaRequestService {

    private final Map<String, IgaRequest> requests = new ConcurrentHashMap<>();
    private final AtomicLong sequence;
    private final ProvisioningSimulator provisioning;

    public IgaRequestService(MockIgaProperties properties, ProvisioningSimulator provisioning) {
        this.sequence = new AtomicLong(properties.requestIdStart());
        this.provisioning = provisioning;
    }

    public IgaRequestSnapshot create(IgaRequestType type, String userId, List<String> entitlements,
                                     String externalReference, String requestedBy, String justification) {
        String requestId = "REQ-" + sequence.getAndIncrement();
        List<String> distinct = List.copyOf(new LinkedHashSet<>(entitlements));
        String by = StringUtils.hasText(requestedBy) ? requestedBy : userId;
        IgaRequest request = new IgaRequest(requestId, type == null ? IgaRequestType.GRANT : type, userId, distinct,
                externalReference, by, justification);
        requests.put(requestId, request);
        return request.snapshot();
    }

    public IgaRequestSnapshot get(String requestId) {
        return find(requestId).snapshot();
    }

    /** Newest first; optionally filtered by status. */
    public List<IgaRequestSnapshot> list(IgaRequestStatus status) {
        return requests.values().stream()
                .map(IgaRequest::snapshot)
                .filter(s -> status == null || s.status() == status)
                .sorted(Comparator.comparing(IgaRequestSnapshot::createdAt).reversed()
                        .thenComparing(IgaRequestSnapshot::requestId, Comparator.reverseOrder()))
                .toList();
    }

    public IgaRequestSnapshot approve(String requestId, String approver, String comment) {
        IgaRequest request = find(requestId);
        request.approve(approverOrDefault(approver), comment);
        if (provisioning.autoProvisionOnApprove()) {
            provisioning.scheduleAutoProvisioning(request);
        }
        return request.snapshot();
    }

    public IgaRequestSnapshot reject(String requestId, String approver, String comment) {
        IgaRequest request = find(requestId);
        request.reject(approverOrDefault(approver), comment);
        return request.snapshot();
    }

    public IgaRequestSnapshot cancel(String requestId, String actor, String comment) {
        IgaRequest request = find(requestId);
        request.cancel(StringUtils.hasText(actor) ? actor : "requesting-system",
                StringUtils.hasText(comment) ? comment : "Withdrawn by the requesting system");
        return request.snapshot();
    }

    public IgaRequestSnapshot provision(String requestId, String actor) {
        IgaRequest request = find(requestId);
        request.startProvisioning(StringUtils.hasText(actor) ? actor : "system");
        provisioning.scheduleCompletion(request);
        return request.snapshot();
    }

    private IgaRequest find(String requestId) {
        IgaRequest request = requests.get(requestId);
        if (request == null) {
            throw new RequestNotFoundException(requestId);
        }
        return request;
    }

    private static String approverOrDefault(String approver) {
        return StringUtils.hasText(approver) ? approver : "manager";
    }
}
