package com.mockiga.service;

import com.mockiga.domain.IgaRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Demo mode: approval alone drives the request through PROVISIONING to PROVISIONED. */
@SpringBootTest(properties = {
        "mock-iga.provisioning.auto-provision-on-approve=true",
        "mock-iga.provisioning.start-delay=100ms",
        "mock-iga.provisioning.duration=100ms"})
class AutoProvisioningTest {

    @Autowired
    private IgaRequestService service;

    @Test
    void approvalTriggersProvisioning() {
        String id = service.create(null, "NT10036", List.of("NOVATECH_VPN"), null, null, null).requestId();

        assertThat(service.approve(id, "manager", null).status()).isEqualTo(IgaRequestStatus.APPROVED);

        await().atMost(Duration.ofSeconds(5))
                .until(() -> service.get(id).status() == IgaRequestStatus.PROVISIONED);
        assertThat(service.get(id).history()).extracting(e -> e.status()).containsExactly(
                IgaRequestStatus.CREATED, IgaRequestStatus.PENDING_APPROVAL, IgaRequestStatus.APPROVED,
                IgaRequestStatus.PROVISIONING, IgaRequestStatus.PROVISIONED);
    }

    @Test
    void manualProvisionDuringAutoDelayDoesNotDoubleProvision() {
        String id = service.create(null, "NT10036", List.of("NOVATECH_VPN"), null, null, null).requestId();
        service.approve(id, "manager", null);
        service.provision(id, "operator");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> service.get(id).status() == IgaRequestStatus.PROVISIONED);
        // Give the auto-start task time to fire; it must be a no-op.
        await().pollDelay(Duration.ofMillis(300)).until(() -> true);
        assertThat(service.get(id).history()).hasSize(5);
    }
}
