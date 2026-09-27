package com.accessorchestrator.iga;

/**
 * Port to the Identity Governance &amp; Administration system (Saviynt in production, a mock for the POC).
 * <p>
 * The IGA is the sole authority for approval and provisioning. This application only submits requests
 * and reads their status; it never grants access itself.
 */
public interface IgaProvider {

    /** Short identifier, e.g. "mock" or "saviynt". */
    String name();

    AccessRequestResponse createAccessRequest(IgaAccessRequest request);

    AccessRequestStatus getRequestStatus(String igaRequestId);

    /** Withdraws a request that has not started provisioning (e.g. the person left the project). */
    AccessRequestStatus cancelRequest(String igaRequestId, String actor, String comment);
}
