package com.mockiga.service;

import com.mockiga.config.MockIgaProperties;
import com.mockiga.domain.IgaRequest;
import com.mockiga.domain.InvalidTransitionException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Simulates target-system provisioning with configurable delays so the lifecycle is visible in a demo. */
@Component
public class ProvisioningSimulator {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningSimulator.class);

    private final MockIgaProperties.Provisioning config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mock-iga-provisioner");
        t.setDaemon(true);
        return t;
    });

    public ProvisioningSimulator(MockIgaProperties properties) {
        this.config = properties.provisioning();
    }

    public boolean autoProvisionOnApprove() {
        return config.autoProvisionOnApprove();
    }

    /** After approval (auto mode): wait, then APPROVED -> PROVISIONING -> (duration) -> PROVISIONED. */
    public void scheduleAutoProvisioning(IgaRequest request) {
        schedule(() -> {
            try {
                request.startProvisioning("system");
            } catch (InvalidTransitionException alreadyStarted) {
                return; // someone called /provision manually in the meantime; that path schedules completion
            }
            scheduleCompletion(request);
        }, config.startDelay().toMillis());
    }

    /** PROVISIONING -> PROVISIONED after the configured duration. */
    public void scheduleCompletion(IgaRequest request) {
        schedule(request::completeProvisioning, config.duration().toMillis());
    }

    private void schedule(Runnable task, long delayMillis) {
        scheduler.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("Provisioning step failed: {}", e.getMessage());
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
