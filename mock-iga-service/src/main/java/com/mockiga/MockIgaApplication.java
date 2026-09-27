package com.mockiga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Mock IGA Service: simulates the external Identity Governance &amp; Administration system (approval and
 * provisioning authority) that the Access Orchestrator integrates with.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MockIgaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockIgaApplication.class, args);
    }
}
