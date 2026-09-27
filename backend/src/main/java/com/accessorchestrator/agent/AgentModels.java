package com.accessorchestrator.agent;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import com.accessorchestrator.domain.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Tool results (kept small; they are sent to the LLM) and the chat API payloads. */
public final class AgentModels {

    private AgentModels() {
    }

    // --- tool results ---------------------------------------------------------------------------------

    public record UserView(String userId, String name, String role, String department, String email) {
    }

    public record ProjectView(Long projectId, String projectCode, String projectName, String description) {
    }

    /** @param reason why the project needs it (null for entitlements outside a project context) */
    public record AccessItem(Long entitlementId, String application, String entitlementCode,
                             String entitlementName, RiskLevel riskLevel, String reason) {
    }

    /** A missing entitlement that is already requested and still in progress. */
    public record InFlightItem(String entitlementCode, String requestId, RequestStatus status) {
    }

    /**
     * @param requestableEntitlementIds missing minus already-in-flight; the only ids createAccessRequest accepts
     * @param verification              e.g. "5 required - 2 already granted = 3 missing"
     * @param nextStep                  instruction for the assistant, derived from the data
     */
    /**
     * The signed-in user's access compared with a colleague's. {@code restricted} items are the colleague's
     * high-risk access: only application and risk are shown. {@code requestableFor} names the user's own project
     * whose required access includes the item.
     */
    public record ColleagueComparisonView(String colleagueUserId, String colleagueName, boolean fullDetail,
                                          List<ComparedItem> colleagueHasYouDont, List<ComparedItem> youHaveTheyDont,
                                          int inCommonCount, String nextStep) {
    }

    public record ComparedItem(String application, String entitlementCode, String entitlementName,
                               RiskLevel riskLevel, boolean restricted, Long requestableForProjectId,
                               String requestableForProject, String note) {
    }

    public record MissingAccessView(String userId, String userName, String role, Long projectId, String projectName,
                                    List<AccessItem> alreadyHave, List<AccessItem> missing,
                                    List<InFlightItem> alreadyRequested, List<Long> requestableEntitlementIds,
                                    String verification, String nextStep) {
    }

    /**
     * @param requestId             the IGA request id shown to the user (e.g. REQ-10173)
     * @param orchestratorReference this application's own id for the request
     */
    public record AccessRequestView(String requestId, String orchestratorReference, RequestType type,
                                    String projectName, RequestStatus status, String statusLabel,
                                    List<AccessItem> requested) {
    }

    public record EmployeeView(String userId, String name, String role, String department, boolean active) {
    }

    public record MemberView(String userId, String name, String projectRole, LocalDate joinedDate) {
    }

    /** Someone who reports to the signed-in user. @param level 1 = direct report, 2 = their report, ... */
    public record ReportView(String userId, String name, String role, int level, String managerName,
                             List<String> projects) {
    }

    /**
     * What removing someone from a project would do (computed by the system).
     *
     * @param authority        why the signed-in user may do this, e.g. "direct report" or "self-removal"
     * @param requestsToCancel ids of their pending grant requests for this project that will be withdrawn
     * @param blockers         why it cannot run yet; empty when canProceed
     */
    public record RemovalPreviewView(String userId, String employeeName, Long projectId, String projectName,
                                     String projectRole, boolean selfRemoval, String authority,
                                     List<AccessItem> toRevoke,
                                     List<AccessItem> keptDefault, List<String> requestsToCancel,
                                     List<String> blockers, boolean canProceed, String nextStep) {
    }

    /** @param revocationRequestId the IGA request id (null when nothing needed revoking) */
    public record RemovalResultView(String userId, String employeeName, String projectName, String reason,
                                    String revocationRequestId, String revocationStatusLabel,
                                    List<AccessItem> revoking, List<AccessItem> keptDefault,
                                    List<String> cancelledRequests) {
    }

    /**
     * What adding someone to a project would do (computed by the system).
     *
     * @param missingAccess    required access they do not have yet (information only; nothing is requested)
     * @param alreadyRequested missing access already in an in-progress request
     */
    public record AdditionPreviewView(String userId, String employeeName, Long projectId, String projectName,
                                      String projectRole, String authority, List<AccessItem> alreadyHave,
                                      List<AccessItem> missingAccess, List<InFlightItem> alreadyRequested,
                                      List<String> blockers, boolean canProceed, String nextStep) {
    }

    /** @param missingAccess required access they still need to request themselves */
    public record AdditionResultView(String userId, String employeeName, String projectName, String projectRole,
                                     List<AccessItem> missingAccess) {
    }

    public record ToolCallLog(String tool, boolean success, String error) {
    }

    // --- chat API ------------------------------------------------------------------------------------

    /**
     * @param selectedEntitlementIds optional: the entitlements the user ticked in the UI when confirming. The
     *                               backend narrows the pending proposal to them before the model runs.
     * @param removalReason          optional: set by the UI's "Confirm removal" button. Counts as an explicit
     *                               confirmation of the pending removal, with this exact reason.
     * @param confirmAddition        optional: set by the UI's "Confirm & add" button; explicit confirmation of the
     *                               pending addition.
     */
    public record ChatRequest(@Size(max = 64) String conversationId,
                              @NotBlank @Size(max = 2000) String message,
                              @Size(max = 50) List<@NotNull Long> selectedEntitlementIds,
                              @Size(max = 500) String removalReason,
                              Boolean confirmAddition) {
    }

    /**
     * @param awaitingConfirmation true when missing access has been presented and a "yes" would submit it
     * @param analysis             structured gap result if the agent ran the analysis this turn
     * @param accessRequest        the request created this turn, if any
     * @param toolCalls            tools the agent used this turn, in order (transparency / debugging)
     * @param pendingAction        what a "yes" would do next: ACCESS_REQUEST, PROJECT_REMOVAL, PROJECT_ADDITION
     *                             or null (the first of {@code pendingActions})
     * @param pendingActions       everything awaiting confirmation (a "move" is a removal and an addition)
     * @param removalPreview       removal preview shown this turn, if any
     * @param removal              the removal carried out this turn, if any
     * @param additionPreview      addition preview shown this turn, if any
     * @param addition             the addition carried out this turn, if any
     */
    public record ChatResponse(String conversationId, String userId, String reply, boolean awaitingConfirmation,
                               MissingAccessView analysis, AccessRequestView accessRequest,
                               List<ToolCallLog> toolCalls, String pendingAction, List<String> pendingActions,
                               RemovalPreviewView removalPreview, RemovalResultView removal,
                               AdditionPreviewView additionPreview, AdditionResultView addition) {
    }

    static String label(RequestStatus status, RequestType type) {
        boolean revoke = type == RequestType.REVOKE;
        return switch (status) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case PENDING_APPROVAL -> "Pending Approval";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case PROVISIONING -> revoke ? "Revoking" : "Provisioning";
            case PROVISIONED -> revoke ? "Revoked" : "Provisioned";
            case FAILED -> "Failed";
            case CANCELLED -> "Cancelled";
        };
    }
}
