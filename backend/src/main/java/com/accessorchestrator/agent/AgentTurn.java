package com.accessorchestrator.agent;

import com.accessorchestrator.agent.AgentModels.AccessRequestView;
import com.accessorchestrator.agent.AgentModels.AdditionPreviewView;
import com.accessorchestrator.agent.AgentModels.AdditionResultView;
import com.accessorchestrator.agent.AgentModels.MissingAccessView;
import com.accessorchestrator.agent.AgentModels.RemovalPreviewView;
import com.accessorchestrator.agent.AgentModels.RemovalResultView;
import com.accessorchestrator.agent.AgentModels.ToolCallLog;
import org.springframework.ai.chat.model.ToolContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Everything the tools need to know about the current turn, passed through Spring AI's {@link ToolContext}
 * (never through the prompt, so the LLM cannot change who the user is). Also collects what happened.
 */
public final class AgentTurn {

    static final String CONTEXT_KEY = "agentTurn";

    private final String userId;
    private final ConversationState conversation;
    private final int turn;
    private final String userMessage;
    private final boolean explicitRemoval;
    private final boolean explicitAddition;
    private final List<ToolCallLog> toolCalls = new ArrayList<>();
    private MissingAccessView analysis;
    private AccessRequestView createdRequest;
    private RemovalPreviewView removalPreview;
    private RemovalResultView removal;
    private AdditionPreviewView additionPreview;
    private AdditionResultView addition;

    /**
     * @param explicitRemoval  the user confirmed the pending removal with its UI button
     * @param explicitAddition the user confirmed the pending addition with its UI button
     */
    AgentTurn(String userId, ConversationState conversation, int turn, String userMessage,
              boolean explicitRemoval, boolean explicitAddition) {
        this.userId = userId;
        this.conversation = conversation;
        this.turn = turn;
        this.userMessage = userMessage;
        this.explicitRemoval = explicitRemoval;
        this.explicitAddition = explicitAddition;
    }

    static AgentTurn from(ToolContext toolContext) {
        Object turn = toolContext == null ? null : toolContext.getContext().get(CONTEXT_KEY);
        if (!(turn instanceof AgentTurn agentTurn)) {
            throw new IllegalStateException("Agent tools must be called with an AgentTurn in the ToolContext");
        }
        return agentTurn;
    }

    /** Runs a tool body for the current turn and records its outcome (errors are rethrown to the model). */
    static <T> T run(ToolContext toolContext, String tool, Function<AgentTurn, T> body) {
        AgentTurn turn = from(toolContext);
        try {
            T result = body.apply(turn);
            turn.log(tool, true, null);
            return result;
        } catch (RuntimeException e) {
            turn.log(tool, false, e.getMessage());
            throw e;
        }
    }

    public String userId() { return userId; }
    public ConversationState conversation() { return conversation; }
    public int turn() { return turn; }
    public String userMessage() { return userMessage; }
    public boolean explicitRemoval() { return explicitRemoval; }
    public boolean explicitAddition() { return explicitAddition; }

    synchronized void log(String tool, boolean success, String error) {
        toolCalls.add(new ToolCallLog(tool, success, error));
    }

    synchronized List<ToolCallLog> toolCalls() { return List.copyOf(toolCalls); }

    synchronized boolean attempted(String tool) {
        return toolCalls.stream().anyMatch(t -> t.tool().equals(tool));
    }

    synchronized void analysis(MissingAccessView analysis) { this.analysis = analysis; }

    synchronized MissingAccessView analysis() { return analysis; }

    synchronized void createdRequest(AccessRequestView request) { this.createdRequest = request; }

    synchronized AccessRequestView createdRequest() { return createdRequest; }

    synchronized void removalPreview(RemovalPreviewView preview) { this.removalPreview = preview; }

    synchronized RemovalPreviewView removalPreview() { return removalPreview; }

    synchronized void removal(RemovalResultView result) { this.removal = result; }

    synchronized RemovalResultView removal() { return removal; }

    synchronized void additionPreview(AdditionPreviewView preview) { this.additionPreview = preview; }

    synchronized AdditionPreviewView additionPreview() { return additionPreview; }

    synchronized void addition(AdditionResultView result) { this.addition = result; }

    synchronized AdditionResultView addition() { return addition; }

    /** Something with side effects was actually done this turn. */
    synchronized boolean actedThisTurn() { return createdRequest != null || removal != null || addition != null; }
}
