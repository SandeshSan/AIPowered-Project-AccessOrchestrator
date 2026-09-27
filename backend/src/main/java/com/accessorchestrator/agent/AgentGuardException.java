package com.accessorchestrator.agent;

/**
 * A tool call refused by a guardrail. Its message is returned to the LLM as the tool result so it can
 * correct course (e.g. ask the user for confirmation) instead of failing the conversation.
 */
public class AgentGuardException extends RuntimeException {

    public AgentGuardException(String message) {
        super(message);
    }
}
