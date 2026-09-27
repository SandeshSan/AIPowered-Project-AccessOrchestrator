package com.accessorchestrator.web;

import com.accessorchestrator.agent.AccessAgentService;
import com.accessorchestrator.agent.AgentModels.ChatRequest;
import com.accessorchestrator.agent.AgentModels.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chat endpoint for the AI Access Agent. The signed-in user is resolved by {@link CurrentUser}. */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AccessAgentService agentService;
    private final CurrentUser currentUser;

    public AgentController(AccessAgentService agentService, CurrentUser currentUser) {
        this.agentService = agentService;
        this.currentUser = currentUser;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(currentUser.id(), request);
    }
}
