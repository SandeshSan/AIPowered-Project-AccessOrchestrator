package com.accessorchestrator.agent;

/** The AI model is not configured or its provider failed (503 / 502). */
public class AgentUnavailableException extends RuntimeException {

    private final boolean notConfigured;

    public AgentUnavailableException(String message, boolean notConfigured, Throwable cause) {
        super(message, cause);
        this.notConfigured = notConfigured;
    }

    public boolean isNotConfigured() {
        return notConfigured;
    }
}
