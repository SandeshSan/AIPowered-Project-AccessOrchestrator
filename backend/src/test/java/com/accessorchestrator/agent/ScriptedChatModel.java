package com.accessorchestrator.agent;

import com.jayway.jsonpath.JsonPath;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Stand-in for an LLM: each call returns the next scripted assistant message. Tool calls are executed with
 * Spring AI's real {@link ToolCallingManager} (as OpenAiChatModel does internally), so tool schemas,
 * argument binding, ToolContext and error handling are all exercised for real.
 */
class ScriptedChatModel implements ChatModel {

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    private final Deque<Function<Prompt, AssistantMessage>> steps = new ArrayDeque<>();
    private final List<Prompt> prompts = new ArrayList<>();
    private final AtomicInteger ids = new AtomicInteger();

    void then(Function<Prompt, AssistantMessage> step) {
        steps.add(step);
    }

    void thenCall(String tool, String jsonArgs) {
        then(p -> calls(new String[][]{{tool, jsonArgs}}));
    }

    void thenSay(String text) {
        then(p -> new AssistantMessage(text));
    }

    List<Prompt> prompts() {
        return prompts;
    }

    void reset() {
        steps.clear();
        prompts.clear();
    }

    boolean exhausted() {
        return steps.isEmpty();
    }

    /** Several tool calls in one assistant message (parallel tool calling). */
    AssistantMessage calls(String[][] toolAndArgs) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (String[] ta : toolAndArgs) {
            toolCalls.add(new AssistantMessage.ToolCall("call_" + ids.incrementAndGet(), "function", ta[0], ta[1]));
        }
        return AssistantMessage.builder().content("").toolCalls(toolCalls).build();
    }

    /** JSON returned by the most recent execution of {@code tool} in this prompt's history. */
    static String lastToolResult(Prompt prompt, String tool) {
        List<Message> messages = prompt.getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                    if (r.name().equals(tool)) {
                        return r.responseData();
                    }
                }
            }
        }
        throw new AssertionError("No result for tool " + tool + " in prompt");
    }

    static <T> T read(Prompt prompt, String tool, String jsonPath) {
        return JsonPath.read(lastToolResult(prompt, tool), jsonPath);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Function<Prompt, AssistantMessage> step = steps.poll();
        if (step == null) {
            throw new AssertionError("ScriptedChatModel ran out of scripted steps");
        }
        AssistantMessage message = step.apply(prompt);
        ChatResponse response = new ChatResponse(List.of(new Generation(message)));
        // Like OpenAiChatModel: execute tools internally only when the caller did not take over the loop
        if (message.hasToolCalls() && ToolCallingChatOptions.isInternalToolExecutionEnabled(prompt.getOptions())) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            return call(new Prompt(result.conversationHistory(), prompt.getOptions()));
        }
        return response;
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
