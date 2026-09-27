package com.mockiga.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stepper view of a request for UIs. GRANT: Request Created -> Pending Approval -> Approved -> Provisioning ->
 * Provisioned. REVOKE: ... -> Revoking -> Revoked. Rejected and cancelled requests end early.
 */
public final class Lifecycle {

    public enum StageState { COMPLETED, CURRENT, UPCOMING, REJECTED, CANCELLED }

    public record Stage(IgaRequestStatus stage, String label, StageState state, Instant at) {
    }

    private static final List<IgaRequestStatus> HAPPY_PATH = List.of(IgaRequestStatus.CREATED,
            IgaRequestStatus.PENDING_APPROVAL, IgaRequestStatus.APPROVED, IgaRequestStatus.PROVISIONING,
            IgaRequestStatus.PROVISIONED);

    private Lifecycle() {
    }

    public static List<Stage> of(IgaRequestSnapshot request) {
        IgaRequestStatus current = request.status();
        List<IgaRequestStatus> path = path(request);
        int currentIndex = path.indexOf(current);

        return path.stream().map(stage -> {
            int i = path.indexOf(stage);
            StageState state;
            if (i < currentIndex) {
                state = StageState.COMPLETED;
            } else if (i == currentIndex) {
                state = switch (current) {
                    case PROVISIONED -> StageState.COMPLETED;
                    case REJECTED -> StageState.REJECTED;
                    case CANCELLED -> StageState.CANCELLED;
                    default -> StageState.CURRENT;
                };
            } else {
                state = StageState.UPCOMING;
            }
            return new Stage(stage, label(stage, request.type()), state, firstTime(request, stage));
        }).toList();
    }

    public static String label(IgaRequestStatus status, IgaRequestType type) {
        boolean revoke = type == IgaRequestType.REVOKE;
        return switch (status) {
            case CREATED -> revoke ? "Revocation Requested" : "Request Created";
            case PENDING_APPROVAL -> "Pending Approval";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case PROVISIONING -> revoke ? "Revoking" : "Provisioning";
            case PROVISIONED -> revoke ? "Revoked" : "Provisioned";
            case CANCELLED -> "Cancelled";
        };
    }

    private static List<IgaRequestStatus> path(IgaRequestSnapshot request) {
        return switch (request.status()) {
            case REJECTED -> List.of(IgaRequestStatus.CREATED, IgaRequestStatus.PENDING_APPROVAL, IgaRequestStatus.REJECTED);
            case CANCELLED -> {
                List<IgaRequestStatus> path = new ArrayList<>(List.of(IgaRequestStatus.CREATED,
                        IgaRequestStatus.PENDING_APPROVAL));
                if (firstTime(request, IgaRequestStatus.APPROVED) != null) {
                    path.add(IgaRequestStatus.APPROVED);
                }
                path.add(IgaRequestStatus.CANCELLED);
                yield path;
            }
            default -> HAPPY_PATH;
        };
    }

    private static Instant firstTime(IgaRequestSnapshot request, IgaRequestStatus stage) {
        return request.history().stream()
                .filter(e -> e.status() == stage)
                .map(StatusEvent::at)
                .findFirst()
                .orElse(null);
    }
}
