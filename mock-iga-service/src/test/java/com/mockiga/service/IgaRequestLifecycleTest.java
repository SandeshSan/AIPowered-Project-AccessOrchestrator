package com.mockiga.service;

import com.mockiga.domain.IgaRequest;
import com.mockiga.domain.IgaRequestSnapshot;
import com.mockiga.domain.IgaRequestStatus;
import com.mockiga.domain.InvalidTransitionException;
import com.mockiga.domain.Lifecycle;
import com.mockiga.domain.Lifecycle.StageState;
import com.mockiga.domain.StatusEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State machine and stepper view of a single request. */
class IgaRequestLifecycleTest {

    private final IgaRequest request = new IgaRequest("REQ-1", com.mockiga.domain.IgaRequestType.GRANT, "NT10036",
            List.of("NOVATECH_JIRA", "NOVATECH_DB_READ", "NOVATECH_VPN"), null, "NT10036", null);

    @Test
    void newRequestIsPendingApprovalWithCreatedInHistory() {
        IgaRequestSnapshot s = request.snapshot();

        assertThat(s.status()).isEqualTo(IgaRequestStatus.PENDING_APPROVAL);
        assertThat(s.history()).extracting(StatusEvent::status)
                .containsExactly(IgaRequestStatus.CREATED, IgaRequestStatus.PENDING_APPROVAL);
        assertThat(states(s)).containsExactly(StageState.COMPLETED, StageState.CURRENT, StageState.UPCOMING,
                StageState.UPCOMING, StageState.UPCOMING);
    }

    @Test
    void happyPathReachesProvisioned() {
        request.approve("manager", "ok");
        assertThat(states(request.snapshot())).containsExactly(StageState.COMPLETED, StageState.COMPLETED,
                StageState.CURRENT, StageState.UPCOMING, StageState.UPCOMING);

        request.startProvisioning("system");
        request.completeProvisioning();

        IgaRequestSnapshot s = request.snapshot();
        assertThat(s.status()).isEqualTo(IgaRequestStatus.PROVISIONED);
        assertThat(s.decidedBy()).isEqualTo("manager");
        assertThat(s.history()).extracting(StatusEvent::status).containsExactly(IgaRequestStatus.CREATED,
                IgaRequestStatus.PENDING_APPROVAL, IgaRequestStatus.APPROVED, IgaRequestStatus.PROVISIONING,
                IgaRequestStatus.PROVISIONED);
        assertThat(Lifecycle.of(s)).extracting(Lifecycle.Stage::label).containsExactly("Request Created",
                "Pending Approval", "Approved", "Provisioning", "Provisioned");
        assertThat(states(s)).containsOnly(StageState.COMPLETED);
        assertThat(Lifecycle.of(s)).allSatisfy(stage -> assertThat(stage.at()).isNotNull());
    }

    @Test
    void rejectionEndsTheLifecycle() {
        request.reject("manager", "not justified");

        IgaRequestSnapshot s = request.snapshot();
        assertThat(Lifecycle.of(s)).extracting(Lifecycle.Stage::label)
                .containsExactly("Request Created", "Pending Approval", "Rejected");
        assertThat(states(s)).containsExactly(StageState.COMPLETED, StageState.COMPLETED, StageState.REJECTED);
        assertThatThrownBy(() -> request.approve("manager", null)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> request.startProvisioning("x")).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void cannotProvisionBeforeApproval() {
        assertThatThrownBy(() -> request.startProvisioning("x"))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("PENDING_APPROVAL");
        assertThatThrownBy(request::completeProvisioning).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void cannotApproveTwice() {
        request.approve("manager", null);
        assertThatThrownBy(() -> request.approve("manager", null)).isInstanceOf(InvalidTransitionException.class);
        assertThatThrownBy(() -> request.reject("manager", null)).isInstanceOf(InvalidTransitionException.class);
    }

    private static List<StageState> states(IgaRequestSnapshot s) {
        return Lifecycle.of(s).stream().map(Lifecycle.Stage::state).toList();
    }
}
