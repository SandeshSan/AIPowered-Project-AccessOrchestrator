package com.accessorchestrator.dto;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.dto.RequestLifecycle.StageDto;
import com.accessorchestrator.dto.RequestLifecycle.StageState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.accessorchestrator.dto.RequestLifecycle.StageState.COMPLETED;
import static com.accessorchestrator.dto.RequestLifecycle.StageState.CURRENT;
import static com.accessorchestrator.dto.RequestLifecycle.StageState.FAILED;
import static com.accessorchestrator.dto.RequestLifecycle.StageState.REJECTED;
import static com.accessorchestrator.dto.RequestLifecycle.StageState.UPCOMING;
import static org.assertj.core.api.Assertions.assertThat;

class RequestLifecycleTest {

    @Test
    void happyPathLabels() {
        assertThat(RequestLifecycle.of(RequestStatus.DRAFT)).extracting(StageDto::label).containsExactly(
                "Request Created", "Pending Approval", "Approved", "Provisioning", "Provisioned");
    }

    @Test
    void statesFollowStatus() {
        assertThat(states(RequestStatus.DRAFT)).containsExactly(CURRENT, UPCOMING, UPCOMING, UPCOMING, UPCOMING);
        assertThat(states(RequestStatus.PENDING_APPROVAL))
                .containsExactly(COMPLETED, CURRENT, UPCOMING, UPCOMING, UPCOMING);
        assertThat(states(RequestStatus.APPROVED)).containsExactly(COMPLETED, COMPLETED, CURRENT, UPCOMING, UPCOMING);
        assertThat(states(RequestStatus.PROVISIONING))
                .containsExactly(COMPLETED, COMPLETED, COMPLETED, CURRENT, UPCOMING);
        assertThat(states(RequestStatus.PROVISIONED))
                .containsExactly(COMPLETED, COMPLETED, COMPLETED, COMPLETED, COMPLETED);
        assertThat(states(RequestStatus.FAILED)).containsExactly(COMPLETED, COMPLETED, COMPLETED, FAILED, UPCOMING);
    }

    @Test
    void rejectedPathIsShorter() {
        assertThat(RequestLifecycle.of(RequestStatus.REJECTED)).extracting(StageDto::label)
                .containsExactly("Request Created", "Pending Approval", "Rejected");
        assertThat(states(RequestStatus.REJECTED)).containsExactly(COMPLETED, COMPLETED, REJECTED);
    }

    @Test
    void revocationLabels() {
        assertThat(RequestLifecycle.of(RequestStatus.PROVISIONING, com.accessorchestrator.domain.RequestType.REVOKE))
                .extracting(StageDto::label)
                .containsExactly("Revocation Requested", "Pending Approval", "Approved", "Revoking", "Revoked");
    }

    @Test
    void cancelledPath() {
        assertThat(RequestLifecycle.of(RequestStatus.CANCELLED)).extracting(StageDto::label)
                .containsExactly("Request Created", "Pending Approval", "Cancelled");
        assertThat(states(RequestStatus.CANCELLED)).containsExactly(COMPLETED, COMPLETED, StageState.CANCELLED);
    }

    private static List<StageState> states(RequestStatus status) {
        return RequestLifecycle.of(status).stream().map(StageDto::state).toList();
    }
}
