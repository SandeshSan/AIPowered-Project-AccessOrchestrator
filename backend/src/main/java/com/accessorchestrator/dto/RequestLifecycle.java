package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Stepper model for UIs, derived from a request's status:
 * Request Created -> Pending Approval -> Approved -> Provisioning -> Provisioned
 * (or Request Created -> Pending Approval -> Rejected).
 */
public final class RequestLifecycle {

    public enum Stage { REQUEST_CREATED, PENDING_APPROVAL, APPROVED, REJECTED, CANCELLED, PROVISIONING, PROVISIONED }

    public enum StageState { COMPLETED, CURRENT, UPCOMING, REJECTED, CANCELLED, FAILED }

    public record StageDto(Stage stage, String label, StageState state) {
    }

    private static final List<Stage> HAPPY_PATH = List.of(Stage.REQUEST_CREATED, Stage.PENDING_APPROVAL,
            Stage.APPROVED, Stage.PROVISIONING, Stage.PROVISIONED);

    private static final List<Stage> REJECTED_PATH = List.of(Stage.REQUEST_CREATED, Stage.PENDING_APPROVAL,
            Stage.REJECTED);

    private static final List<Stage> CANCELLED_PATH = List.of(Stage.REQUEST_CREATED, Stage.PENDING_APPROVAL,
            Stage.CANCELLED);

    private RequestLifecycle() {
    }

    public static List<StageDto> of(RequestStatus status) {
        return of(status, RequestType.GRANT);
    }

    /** Revocations read "... -> Revoking -> Revoked". */
    public static List<StageDto> of(RequestStatus status, RequestType type) {
        List<Stage> path = switch (status) {
            case REJECTED -> REJECTED_PATH;
            case CANCELLED -> CANCELLED_PATH;
            default -> HAPPY_PATH;
        };
        int current = switch (status) {
            case DRAFT -> 0;
            case SUBMITTED, PENDING_APPROVAL -> 1;
            case APPROVED, REJECTED, CANCELLED -> 2;
            case PROVISIONING, FAILED -> 3;
            case PROVISIONED -> 4;
        };
        StageState atCurrent = switch (status) {
            case PROVISIONED -> StageState.COMPLETED;
            case REJECTED -> StageState.REJECTED;
            case CANCELLED -> StageState.CANCELLED;
            case FAILED -> StageState.FAILED;
            default -> StageState.CURRENT;
        };
        return IntStream.range(0, path.size())
                .mapToObj(i -> new StageDto(path.get(i), label(path.get(i), type),
                        i < current ? StageState.COMPLETED : i == current ? atCurrent : StageState.UPCOMING))
                .toList();
    }

    private static String label(Stage stage, RequestType type) {
        boolean revoke = type == RequestType.REVOKE;
        return switch (stage) {
            case REQUEST_CREATED -> revoke ? "Revocation Requested" : "Request Created";
            case PENDING_APPROVAL -> "Pending Approval";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case CANCELLED -> "Cancelled";
            case PROVISIONING -> revoke ? "Revoking" : "Provisioning";
            case PROVISIONED -> revoke ? "Revoked" : "Provisioned";
        };
    }
}
