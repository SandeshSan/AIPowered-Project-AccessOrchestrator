package com.accessorchestrator.agent;

import com.accessorchestrator.agent.AgentModels.ChatRequest;
import com.accessorchestrator.agent.AgentModels.ChatResponse;
import com.accessorchestrator.dto.RemovalDtos.ReportDto;
import com.accessorchestrator.dto.UserDto;
import com.accessorchestrator.service.AccessAuthority;
import com.accessorchestrator.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Runs one conversational turn of the Access Agent: the LLM plans and explains, the tools do the work.
 * <p>
 * The tool loop is run here (not inside the model) so each turn can be remembered <i>with</i> its tool calls
 * and results. Remembering only the reply text teaches the model that "yes" is answered with a success
 * message, and it starts inventing submissions without calling the tool. As a second line of defence, a
 * reply that claims a submission the system did not make is corrected once and otherwise replaced.
 */
@Service
public class AccessAgentService {

    private static final Logger log = LoggerFactory.getLogger(AccessAgentService.class);
    private static final String KEY_NOT_SET = "not-set";
    private static final int MAX_TOOL_ROUNDS = 8;

    /** Wording that asserts a request was submitted or a person was removed. */
    private static final Pattern CLAIMS_ACTION = Pattern.compile(
            "(?i)(submitted successfully|request (has been |was |is )?submitted|request id:\\s*req-"
                    + "|(has been|have been|was|were|is now) removed from|removal (is )?complete"
                    + "|(has been|have been|was|were|is now) added to)");

    static final String CORRECTION = "System check: the pending action was not carried out in this turn. Never "
            + "claim a request was submitted or a person was added or removed unless that tool succeeded in the "
            + "current turn. If the user's latest message confirms the pending action(s) in the session context, call "
            + "exactly the tool named there with the ids given there: createAccessRequest only for the signed-in "
            + "user's own access, addEmployeeToProject to add someone to a project, removeEmployeeFromProject to "
            + "remove someone. Otherwise do not claim anything happened; ask the user to confirm.";

    static final String NOT_SUBMITTED = "I wasn't able to complete that, so nothing has been sent for approval. "
            + "Please confirm again (for example with the button), or start a new conversation.";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ToolCallingManager toolCallingManager;
    private final AccessAgentTools tools;
    private final ProjectRemovalTools removalTools;
    private final ProjectAdditionTools additionTools;
    private final AccessAuthority accessAuthority;
    private final ConversationStore conversations;
    private final UserService userService;
    private final String systemPrompt;
    private final String chatProvider;
    private final String openAiKey;

    public AccessAgentService(ObjectProvider<ChatModel> chatModelProvider,
                              ObjectProvider<ToolCallingManager> toolCallingManager,
                              AccessAgentTools tools,
                              ProjectRemovalTools removalTools,
                              ProjectAdditionTools additionTools,
                              AccessAuthority accessAuthority,
                              ConversationStore conversations,
                              UserService userService,
                              @Value("classpath:prompts/access-agent-system.md") Resource systemPrompt,
                              @Value("${spring.ai.model.chat:openai}") String chatProvider,
                              @Value("${spring.ai.openai.api-key:" + KEY_NOT_SET + "}") String openAiKey) {
        this.chatModelProvider = chatModelProvider;
        this.toolCallingManager = toolCallingManager.getIfAvailable(() -> ToolCallingManager.builder().build());
        this.tools = tools;
        this.removalTools = removalTools;
        this.additionTools = additionTools;
        this.accessAuthority = accessAuthority;
        this.conversations = conversations;
        this.userService = userService;
        this.systemPrompt = read(systemPrompt);
        this.chatProvider = chatProvider;
        this.openAiKey = openAiKey;
    }

    public ChatResponse chat(String userId, ChatRequest request) {
        UserDto user = userService.getUser(userId);
        String canonicalUserId = user.userId();
        ChatModel model = model();
        ConversationState conversation = conversations.getOrStart(request.conversationId(), canonicalUserId);

        synchronized (conversation) {
            int turnNumber = conversation.nextTurn();
            boolean explicitRemoval = StringUtils.hasText(request.removalReason());
            boolean explicitAddition = Boolean.TRUE.equals(request.confirmAddition());
            if (explicitRemoval) {
                conversation.confirmRemoval(request.removalReason().trim()); // button: reason may contain "no"
            } else if (explicitAddition) {
                conversation.requireAdditionPending();
            } else if (ConfirmationPolicy.isNegative(request.message())) {
                conversation.clearPendingActions(); // "no" withdraws whatever was on offer
            } else if (request.selectedEntitlementIds() != null && !request.selectedEntitlementIds().isEmpty()) {
                conversation.select(request.selectedEntitlementIds());
            }
            boolean confirming = explicitRemoval || explicitAddition
                    || ((conversation.proposal() != null || conversation.removalProposal() != null
                    || conversation.additionProposal() != null)
                    && ConfirmationPolicy.isAffirmative(request.message()));
            List<ReportDto> reports = accessAuthority.reportsOf(userService.findUser(canonicalUserId));
            AgentTurn turn = new AgentTurn(canonicalUserId, conversation, turnNumber, request.message(),
                    explicitRemoval, explicitAddition);
            // The tool each pending action needs; used to spot a confirmation the model did not act on
            List<String> expectedTools = conversation.pendingActions().stream().map(a -> switch (a) {
                case "ACCESS_REQUEST" -> "createAccessRequest";
                case "PROJECT_REMOVAL" -> "removeEmployeeFromProject";
                default -> "addEmployeeToProject";
            }).toList();

            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(ToolCallbacks.from(tools, removalTools, additionTools))
                    .toolContext(Map.of(AgentTurn.CONTEXT_KEY, turn))
                    .internalToolExecutionEnabled(false)
                    .build();

            UserMessage userMessage = new UserMessage(request.message());
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt + sessionContext(user, reports, conversation)));
            messages.addAll(conversation.history());
            messages.add(userMessage);

            Run run = run(model, messages, options);

            if (!turn.actedThisTurn() && (claimsSubmission(run.reply())
                    || (confirming && expectedTools.stream().noneMatch(turn::attempted)))) {
                log.warn("Turn {} of {}: reply claimed or skipped an action without calling its tool; "
                        + "asking the model to correct it", turnNumber, conversation.id());
                List<Message> retry = new ArrayList<>(messages);
                retry.addAll(run.produced());
                retry.add(new SystemMessage(CORRECTION));
                run = run(model, retry, options);
                if (!turn.actedThisTurn() && claimsSubmission(run.reply())) {
                    log.warn("Turn {} of {}: model still claimed a submission; replacing the reply", turnNumber,
                            conversation.id());
                    run = new Run(NOT_SUBMITTED, List.of(new AssistantMessage(NOT_SUBMITTED)));
                }
            }

            List<Message> remembered = new ArrayList<>();
            remembered.add(userMessage);
            remembered.addAll(run.produced());
            conversation.remember(remembered);

            log.info("Agent turn {} of {} for {}: tools={}", turnNumber, conversation.id(), canonicalUserId,
                    turn.toolCalls().stream()
                            .map(t -> t.success() ? t.tool() : t.tool() + "(failed: " + t.error() + ")").toList());

            List<String> pendingActions = conversation.pendingActions();
            String pendingAction = pendingActions.isEmpty() ? null : pendingActions.getFirst();
            return new ChatResponse(conversation.id(), canonicalUserId, run.reply(), pendingAction != null,
                    turn.analysis(), turn.createdRequest(), turn.toolCalls(), pendingAction, pendingActions,
                    turn.removalPreview(), turn.removal(), turn.additionPreview(), turn.addition());
        }
    }

    /** Messages produced by one model run (tool calls, tool results, final reply) and the reply text. */
    private record Run(String reply, List<Message> produced) {
    }

    private Run run(ChatModel model, List<Message> messages, ToolCallingChatOptions options) {
        int start = messages.size();
        try {
            Prompt prompt = new Prompt(messages, options);
            org.springframework.ai.chat.model.ChatResponse response = model.call(prompt);
            for (int round = 0; response != null && response.hasToolCalls() && round < MAX_TOOL_ROUNDS; round++) {
                ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
                prompt = new Prompt(result.conversationHistory(), options);
                response = model.call(prompt);
            }
            List<Message> produced = new ArrayList<>(prompt.getInstructions().subList(start, prompt.getInstructions().size()));
            AssistantMessage last = response == null || response.getResult() == null ? null : response.getResult().getOutput();
            if (last == null || last.hasToolCalls() || !StringUtils.hasText(last.getText())) {
                String fallback = "Sorry, I couldn't complete that. Please try again.";
                produced.add(new AssistantMessage(fallback));
                return new Run(fallback, produced);
            }
            produced.add(last);
            return new Run(last.getText(), produced);
        } catch (AgentUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Full provider details stay in the server log; the user gets a plain explanation
            log.warn("AI provider call failed (HTTP status {}): {}", AiProviderFailures.httpStatus(e), e.getMessage());
            throw AiProviderFailures.toUserFacing(e);
        }
    }

    static boolean claimsSubmission(String reply) {
        return reply != null && CLAIMS_ACTION.matcher(reply).find();
    }

    /**
     * Authoritative facts for this turn, taken from server state, so the model never has to recall or guess
     * ids from earlier turns.
     */
    static String sessionContext(UserDto user, List<ReportDto> reports, ConversationState conversation) {
        StringBuilder sb = new StringBuilder("\n\n## Session context (provided by the system, authoritative)\n")
                .append("- Signed-in user: userId=").append(user.userId()).append(", name=").append(user.name())
                .append(", role=").append(user.role()).append(".\n")
                .append("- Permissions: may remove themselves from their own projects");
        if (user.admin()) {
            sb.append("; admin, so may also remove anyone from any project");
        }
        if (!reports.isEmpty()) {
            sb.append("; line manager of ").append(reports.stream()
                    .map(r -> r.user().name() + " (" + r.user().userId() + ")"
                            + (r.level() > 1 ? " via " + r.user().managerName() : ""))
                    .toList())
                    .append(", so may view their access (getUserExistingAccess), add them to projects and remove them "
                            + "from any of their projects");
        } else if (!user.admin()) {
            sb.append("; may not remove other people");
        }
        sb.append(".\n");
        ConversationState.AdditionProposal addition = conversation.additionProposal();
        if (addition != null) {
            sb.append("- Awaiting the user's confirmation: add ").append(addition.targetName()).append(" (userId=")
                    .append(addition.targetUserId()).append(") to project ").append(addition.projectName())
                    .append(" (projectId=").append(addition.projectId()).append(") as ").append(addition.projectRole())
                    .append(". If the user's latest message confirms, call addEmployeeToProject with userId=")
                    .append(addition.targetUserId()).append(", projectId=").append(addition.projectId()).append(".\n");
        }
        ConversationState.RemovalProposal removal = conversation.removalProposal();
        if (removal != null) {
            sb.append("- Awaiting the user's confirmation: remove ").append(removal.targetName()).append(" (userId=")
                    .append(removal.targetUserId()).append(") from project ").append(removal.projectName())
                    .append(" (projectId=").append(removal.projectId()).append("). If the user's latest message ")
                    .append("confirms and includes a reason, call removeEmployeeFromProject with userId=")
                    .append(removal.targetUserId()).append(", projectId=").append(removal.projectId())
                    .append(" and their reason. If they confirmed without a reason, ask for one first.\n");
            if (removal.confirmedReason() != null) {
                sb.append("- The user CONFIRMED this removal with the confirm button and gave the reason \"")
                        .append(removal.confirmedReason()).append("\". Call removeEmployeeFromProject now with userId=")
                        .append(removal.targetUserId()).append(", projectId=").append(removal.projectId())
                        .append(" and that reason.\n");
            }
        }
        ConversationState.Proposal proposal = conversation.proposal();
        if (proposal == null) {
            sb.append("- No access request is currently awaiting the user's confirmation.\n");
        } else {
            sb.append("- Awaiting the user's confirmation: missing access for project ").append(proposal.projectName())
                    .append(" (projectId=").append(proposal.projectId()).append(") was shown to the user. If the user's ")
                    .append("latest message confirms, call createAccessRequest with userId=").append(user.userId())
                    .append(", projectId=").append(proposal.projectId()).append(", entitlementIds=")
                    .append(proposal.submittable()).append(". Do not re-run the analysis first.\n");
            if (proposal.isNarrowed()) {
                sb.append("- The user selected only ").append(proposal.selectedIds().size()).append(" of the ")
                        .append(proposal.entitlementIds().size()).append(" proposed entitlements (ids ")
                        .append(proposal.selectedIds()).append("). Request exactly these; do not add the others.\n");
            }
        }
        return sb.toString();
    }

    private ChatModel model() {
        if ("openai".equals(chatProvider) && (!StringUtils.hasText(openAiKey) || KEY_NOT_SET.equals(openAiKey))) {
            throw new AgentUnavailableException("AI agent is not configured: set OPENAI_API_KEY (and optionally "
                    + "OPENAI_BASE_URL / OPENAI_MODEL for another OpenAI-compatible provider)", true, null);
        }
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new AgentUnavailableException("AI agent is not configured: no chat model available", true, null);
        }
        return model;
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read agent system prompt", e);
        }
    }
}
