package com.mockiga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "mock-iga")
public record MockIgaProperties(
        @DefaultValue("10145") long requestIdStart,
        @DefaultValue Provisioning provisioning) {

    public record Provisioning(
            @DefaultValue("true") boolean autoProvisionOnApprove,
            @DefaultValue("2s") Duration startDelay,
            @DefaultValue("3s") Duration duration) {
    }
}
