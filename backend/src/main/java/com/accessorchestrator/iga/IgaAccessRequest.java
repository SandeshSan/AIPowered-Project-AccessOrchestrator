package com.accessorchestrator.iga;

import com.accessorchestrator.domain.RequestType;

import java.util.List;

/**
 * Provider-neutral payload submitted to the IGA system.
 *
 * @param requestId   the orchestrator's own request id, sent as an external reference
 * @param type        GRANT (provision) or REVOKE (deprovision)
 * @param requestedBy who initiated the request (the user themself, or e.g. an onboarding manager)
 */
public record IgaAccessRequest(
        String requestId,
        RequestType type,
        String userId,
        String requestedBy,
        String userEmail,
        String projectCode,
        String justification,
        List<Item> items) {

    public record Item(String application, String entitlementCode, String reason) {
    }
}
