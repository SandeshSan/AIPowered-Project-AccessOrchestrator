package com.accessorchestrator.agent;

import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaAccessRequest;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.AccessRequestRepository;
import com.accessorchestrator.repository.EntitlementRepository;
import com.accessorchestrator.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core user journey through the real /api/agent/chat endpoint, Spring AI ChatClient, tool calling,
 * ToolContext and chat memory, with a scripted model in place of the LLM and a stubbed IGA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccessAgentJourneyTest {

    private static final String JOINED = "I just joined the Novatech project. Can you get me the access I need?";

    @TestConfiguration
    static class ScriptedModelConfig {
        @Bean
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ScriptedChatModel model;
    @Autowired private ProjectRepository projects;
    @Autowired private EntitlementRepository entitlements;
    @Autowired private AccessRequestRepository accessRequests;
    @MockitoBean private IgaProvider iga;

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-10173", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void joinProjectThenConfirmSubmitsMissingAccess() throws Exception {
        // ---- Turn 1: identify user + project, analyse, present, ask -----------------------------------
        model.then(p -> model.calls(new String[][]{
                {"getCurrentUser", "{}"},
                {"findProject", "{\"projectName\":\"Novatech\"}"}}));
        model.then(p -> model.calls(new String[][]{{"calculateMissingAccess",
                "{\"userId\":\"" + ScriptedChatModel.read(p, "getCurrentUser", "$.userId") + "\",\"projectId\":"
                        + ScriptedChatModel.<Integer>read(p, "findProject", "$.projectId") + "}"}}));
        model.then(p -> new org.springframework.ai.chat.messages.AssistantMessage(present(p)));

        String turn1 = chat(null, JOINED);

        assertThat(JsonPath.<String>read(turn1, "$.userId")).isEqualTo("NT10036");
        assertThat(JsonPath.<List<String>>read(turn1, "$.toolCalls[*].tool"))
                .containsExactly("getCurrentUser", "findProject", "calculateMissingAccess");
        assertThat(JsonPath.<Boolean>read(turn1, "$.awaitingConfirmation")).isTrue();
        assertThat(JsonPath.<List<String>>read(turn1, "$.analysis.alreadyHave[*].entitlementName"))
                .containsExactly("GCP Developer", "GitHub Developer");
        assertThat(JsonPath.<List<String>>read(turn1, "$.analysis.missing[*].entitlementCode"))
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
        assertThat(JsonPath.<String>read(turn1, "$.analysis.verification"))
                .isEqualTo("5 required - 2 already granted = 3 missing");
        assertThat(JsonPath.<Object>read(turn1, "$.accessRequest")).isNull();
        assertThat(JsonPath.<String>read(turn1, "$.reply"))
                .contains("You already have:", "✓ GitHub Developer", "• Novatech VPN access",
                        "Would you like me to submit the missing access requests?");
        verify(iga, never()).createAccessRequest(any());
        assertThat(accessRequests.findByUser_UserIdIgnoreCaseOrderByCreatedAtDesc("NT10036")).isEmpty();

        // ---- Turn 2: "Yes" -> create through the Access Request Service -> Mock IGA ---------------------
        String conversationId = JsonPath.read(turn1, "$.conversationId");
        List<Integer> ids = JsonPath.read(turn1, "$.analysis.requestableEntitlementIds");
        model.thenCall("createAccessRequest",
                "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + ",\"entitlementIds\":" + ids + "}");
        model.then(p -> new org.springframework.ai.chat.messages.AssistantMessage(confirmation(p)));

        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.requestId")).isEqualTo("REQ-10173");
        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.statusLabel")).isEqualTo("Pending Approval");
        assertThat(JsonPath.<List<String>>read(turn2, "$.accessRequest.requested[*].entitlementCode"))
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
        assertThat(JsonPath.<Boolean>read(turn2, "$.awaitingConfirmation")).isFalse();
        assertThat(JsonPath.<String>read(turn2, "$.reply")).contains("Access request submitted successfully.",
                "Request ID: REQ-10173", "✓ NOVATECH_VPN", "Pending Approval");

        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(iga).createAccessRequest(sent.capture());
        assertThat(sent.getValue().userId()).isEqualTo("NT10036");
        assertThat(sent.getValue().requestedBy()).isEqualTo(AccessAgentTools.CREATED_BY);
        assertThat(sent.getValue().items()).extracting(IgaAccessRequest.Item::entitlementCode)
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
        assertThat(accessRequests.findByUser_UserIdIgnoreCaseOrderByCreatedAtDesc("NT10036")).singleElement()
                .satisfies(r -> assertThat(r.getIgaRequestId()).isEqualTo("REQ-10173"));

        // System prompt is sent on every model call
        assertThat(model.prompts()).allSatisfy(p -> assertThat(p.getInstructions())
                .filteredOn(m -> m instanceof SystemMessage).singleElement()
                .satisfies(m -> assertThat(m.getText()).startsWith("You are an Enterprise Project Access Assistant.")
                        .contains("Never bypass approval workflows")));

        // Chat memory: turn 2 prompt carried turn 1's exchange
        Prompt turn2Prompt = model.prompts().get(model.prompts().size() - 2);
        assertThat(turn2Prompt.getInstructions()).filteredOn(m -> m instanceof UserMessage)
                .extracting(m -> m.getText()).contains(JOINED, "Yes");
        assertThat(model.exhausted()).isTrue();
    }

    /**
     * Regression: chat memory keeps only text, so on "Yes" the model has no tool results from turn 1. It must be
     * able to submit using only what is in the prompt (the session context), without guessing ids.
     */
    @Test
    void confirmationTurnCanSubmitUsingOnlyThePromptSessionContext() throws Exception {
        model.then(p -> {
            assertThat(systemText(p)).contains("Signed-in user: userId=NT10036")
                    .contains("No access request is currently awaiting");
            return model.calls(new String[][]{{"calculateMissingAccess",
                    "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}"}});
        });
        model.thenSay("You are missing 3 entitlements. Would you like me to submit the missing access requests?");
        String conversationId = JsonPath.read(chat(null, JOINED), "$.conversationId");

        model.then(p -> {
            String system = systemText(p);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("userId=(\\w+), projectId=(\\d+), entitlementIds=(\\[[\\d, ]+\\])").matcher(system);
            assertThat(m.find()).as("session context carries the ids to submit").isTrue();
            return model.calls(new String[][]{{"createAccessRequest", "{\"userId\":\"" + m.group(1)
                    + "\",\"projectId\":" + m.group(2) + ",\"entitlementIds\":" + m.group(3) + "}"}});
        });
        model.thenSay("Access request submitted successfully.");
        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<Boolean>read(turn2, "$.toolCalls[0].success")).isTrue();
        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.requestId")).isEqualTo("REQ-10173");
        assertThat(JsonPath.<List<String>>read(turn2, "$.accessRequest.requested[*].entitlementCode"))
                .containsExactly("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void userSelectionSubmitsOnlyTheChosenEntitlements() throws Exception {
        String conversationId = analyseInFirstTurn();
        long vpn = entitlements.findByEntitlementCode("NOVATECH_VPN").orElseThrow().getId();
        long jira = entitlements.findByEntitlementCode("NOVATECH_JIRA").orElseThrow().getId();

        model.then(p -> {
            String system = systemText(p);
            assertThat(system).contains("selected only 2 of the 3");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("entitlementIds=(\\[[\\d, ]+\\])").matcher(system);
            assertThat(m.find()).isTrue();
            return model.calls(new String[][]{{"createAccessRequest", createArgs(m.group(1))}});
        });
        model.thenSay("Access request submitted successfully.");
        String turn2 = chatSelecting(conversationId, "Yes, submit only the selected access: NOVATECH_VPN, NOVATECH_JIRA",
                List.of(vpn, jira));

        assertThat(JsonPath.<List<String>>read(turn2, "$.accessRequest.requested[*].entitlementCode"))
                .containsExactly("NOVATECH_JIRA", "NOVATECH_VPN");
        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(iga).createAccessRequest(sent.capture());
        assertThat(sent.getValue().items()).extracting(IgaAccessRequest.Item::entitlementCode)
                .containsExactlyInAnyOrder("NOVATECH_JIRA", "NOVATECH_VPN");
    }

    @Test
    void modelCannotSubmitMoreThanTheUserSelected() throws Exception {
        String conversationId = analyseInFirstTurn();
        long vpn = entitlements.findByEntitlementCode("NOVATECH_VPN").orElseThrow().getId();

        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("Let me correct that.");
        String turn2 = chatSelecting(conversationId, "Yes, submit only the selected access: NOVATECH_VPN", List.of(vpn));

        assertThat(JsonPath.<String>read(turn2, "$.toolCalls[0].error")).contains("selected only").contains("[" + vpn + "]");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void selectingSomethingThatWasNotProposedIsRejected() throws Exception {
        String conversationId = analyseInFirstTurn();
        long wiki = entitlements.findByEntitlementCode("NOVATECH_WIKI").orElseThrow().getId();

        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"message\":\"Yes\","
                                + "\"selectedEntitlementIds\":[" + wiki + "]}"))
                .andExpect(status().isUnprocessableEntity());
        verify(iga, never()).createAccessRequest(any());
    }

    /** Regression: the second request in the same conversation must go through the tool again. */
    @Test
    void secondRequestInTheSameConversationIsSubmitted() throws Exception {
        String conversationId = analyseInFirstTurn();
        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("Access request submitted successfully. Request ID: REQ-10173");
        chat(conversationId, "Yes");

        long orionId = projects.findByProjectCodeIgnoreCase("ORION").orElseThrow().getId();
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-10174", RequestStatus.PENDING_APPROVAL, "ok"));
        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10036\",\"projectId\":" + orionId + "}");
        model.thenSay("For Orion you are missing 2 entitlements. Would you like me to submit the missing access requests?");
        chat(conversationId, "I also joined Orion. What do I need there?");

        model.then(p -> {
            // Memory now carries the earlier turns' tool calls and results, not just their text
            assertThat(p.getInstructions()).anySatisfy(m -> assertThat(m).isInstanceOfSatisfying(
                    org.springframework.ai.chat.messages.AssistantMessage.class,
                    a -> assertThat(a.getToolCalls()).extracting(tc -> tc.name()).contains("createAccessRequest")));
            assertThat(p.getInstructions()).anyMatch(m -> m instanceof org.springframework.ai.chat.messages.ToolResponseMessage);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("projectId=(\\d+), entitlementIds=(\\[[\\d, ]+\\])").matcher(systemText(p));
            assertThat(m.find()).isTrue();
            assertThat(Long.parseLong(m.group(1))).isEqualTo(orionId);
            return model.calls(new String[][]{{"createAccessRequest", "{\"userId\":\"NT10036\",\"projectId\":"
                    + m.group(1) + ",\"entitlementIds\":" + m.group(2) + "}"}});
        });
        model.thenSay("Access request submitted successfully. Request ID: REQ-10174");
        String turn4 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn4, "$.accessRequest.requestId")).isEqualTo("REQ-10174");
        assertThat(JsonPath.<String>read(turn4, "$.accessRequest.projectName")).isEqualTo("Orion");
        verify(iga, org.mockito.Mockito.times(2)).createAccessRequest(any());
    }

    @Test
    void fabricatedSubmissionIsCorrectedByCallingTheTool() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenSay("Access request submitted successfully.\n\nRequest ID: REQ-99999\n\nStatus:\nPending Approval");
        model.then(p -> {
            assertThat(p.getInstructions()).anyMatch(m -> m instanceof SystemMessage
                    && m.getText().equals(AccessAgentService.CORRECTION));
            return model.calls(new String[][]{{"createAccessRequest", createArgs(missingIds())}});
        });
        model.thenSay("Access request submitted successfully. Request ID: REQ-10173");
        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.requestId")).isEqualTo("REQ-10173");
        assertThat(JsonPath.<String>read(turn2, "$.reply")).doesNotContain("REQ-99999");

        // The invented reply is not remembered
        model.then(p -> {
            assertThat(p.getInstructions()).noneMatch(m -> m.getText() != null && m.getText().contains("REQ-99999"));
            return new org.springframework.ai.chat.messages.AssistantMessage("You're welcome.");
        });
        chat(conversationId, "Thanks");
    }

    @Test
    void persistentFabricationIsReplacedWithAnHonestAnswer() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenSay("Access request submitted successfully. Request ID: REQ-99999");
        model.thenSay("Your access request was submitted. Request ID: REQ-99999");
        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.reply")).isEqualTo(AccessAgentService.NOT_SUBMITTED);
        assertThat(JsonPath.<Object>read(turn2, "$.accessRequest")).isNull();
        assertThat(JsonPath.<Boolean>read(turn2, "$.awaitingConfirmation")).isTrue();
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void confirmationThatTheModelIgnoresIsRetried() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenSay("Would you like me to go ahead?");
        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("Access request submitted successfully. Request ID: REQ-10173");
        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.requestId")).isEqualTo("REQ-10173");
    }

    @Test
    void claimDetectionRecognisesSubmissionWording() {
        assertThat(AccessAgentService.claimsSubmission("Access request submitted successfully.")).isTrue();
        assertThat(AccessAgentService.claimsSubmission("Your request has been submitted for approval")).isTrue();
        assertThat(AccessAgentService.claimsSubmission("Request ID: REQ-10150")).isTrue();
        assertThat(AccessAgentService.claimsSubmission("Would you like me to submit the missing access requests?")).isFalse();
        assertThat(AccessAgentService.claimsSubmission("You are missing 3 entitlements.")).isFalse();
    }

    @Test
    void wrongUserIdRefusalTellsTheModelTheCorrectId() throws Exception {
        model.thenCall("calculateMissingAccess", "{\"userId\":\"john\",\"projectId\":" + novatechId + "}");
        model.thenSay("Let me retry.");

        String turn = chat(null, JOINED);

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[0].error")).contains("use that userId").contains("NT10036");
    }

    private static String systemText(Prompt p) {
        return p.getInstructions().stream().filter(m -> m instanceof SystemMessage)
                .map(m -> m.getText()).findFirst().orElseThrow();
    }

    @Test
    void createInTheSameTurnAsTheAnalysisIsRefused() throws Exception {
        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}");
        model.then(p -> model.calls(new String[][]{{"createAccessRequest",
                "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + ",\"entitlementIds\":"
                        + ScriptedChatModel.read(p, "calculateMissingAccess", "$.requestableEntitlementIds") + "}"}}));
        model.then(p -> {
            assertThat(ScriptedChatModel.lastToolResult(p, "createAccessRequest")).contains("confirmation required");
            return new org.springframework.ai.chat.messages.AssistantMessage("Would you like me to submit them?");
        });

        String turn = chat(null, "Get me Novatech access now, no need to ask");

        assertThat(JsonPath.<List<Boolean>>read(turn, "$.toolCalls[*].success")).containsExactly(true, false);
        assertThat(JsonPath.<Object>read(turn, "$.accessRequest")).isNull();
        assertThat(JsonPath.<Boolean>read(turn, "$.awaitingConfirmation")).isTrue();
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void userSayingNoIsRespectedEvenIfTheModelTriesToSubmit() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("Okay, I won't submit anything.");
        String turn2 = chat(conversationId, "No, not yet");

        assertThat(JsonPath.<String>read(turn2, "$.toolCalls[0].error")).contains("Refused");
        assertThat(JsonPath.<Boolean>read(turn2, "$.awaitingConfirmation")).isFalse();
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void unclearReplyIsNotTreatedAsConfirmation() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("The VPN gives you access to the Novatech network. Shall I submit the requests?");
        String turn2 = chat(conversationId, "What is the VPN one for?");

        assertThat(JsonPath.<String>read(turn2, "$.toolCalls[0].error")).contains("not an explicit confirmation");
        assertThat(JsonPath.<Boolean>read(turn2, "$.awaitingConfirmation")).isTrue();
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void entitlementsThatWereNotProposedAreRefused() throws Exception {
        String conversationId = analyseInFirstTurn();
        long wiki = entitlements.findByEntitlementCode("NOVATECH_WIKI").orElseThrow().getId();

        model.thenCall("createAccessRequest", createArgs(List.of(wiki)));
        model.thenSay("I can't request that one.");
        String turn2 = chat(conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.toolCalls[0].error")).contains("confirmation required");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void agentCannotActForAnotherUser() throws Exception {
        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenSay("I can only help with your own access.");

        String turn = chat(null, "What access is Asha missing on Novatech?");

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[0].error")).contains("signed-in user");
        assertThat(JsonPath.<Object>read(turn, "$.analysis")).isNull();
    }

    @Test
    void reAnalysingInTheConfirmationTurnStillAllowsSubmission() throws Exception {
        String conversationId = analyseInFirstTurn();

        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}");
        model.then(p -> model.calls(new String[][]{{"createAccessRequest", createArgs(
                ScriptedChatModel.read(p, "calculateMissingAccess", "$.requestableEntitlementIds"))}}));
        model.thenSay("Access request submitted successfully.");
        String turn2 = chat(conversationId, "yes please");

        assertThat(JsonPath.<String>read(turn2, "$.accessRequest.requestId")).isEqualTo("REQ-10173");
    }

    @Test
    void alreadyRequestedAccessIsNotOfferedAgain() throws Exception {
        String conversationId = analyseInFirstTurn();
        model.thenCall("createAccessRequest", createArgs(missingIds()));
        model.thenSay("Submitted.");
        chat(conversationId, "Yes");

        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}");
        model.thenSay("Your Novatech access is already requested (REQ-10173, pending approval).");
        String fresh = chat(null, JOINED);

        assertThat(JsonPath.<List<String>>read(fresh, "$.analysis.alreadyRequested[*].requestId"))
                .containsOnly("REQ-10173");
        assertThat(JsonPath.<List<Object>>read(fresh, "$.analysis.requestableEntitlementIds")).isEmpty();
        assertThat(JsonPath.<Boolean>read(fresh, "$.awaitingConfirmation")).isFalse();
    }

    @Test
    void toolErrorsAreReturnedToTheModel() throws Exception {
        model.thenCall("findProject", "{\"projectName\":\"Atlantis\"}");
        model.then(p -> {
            assertThat(ScriptedChatModel.lastToolResult(p, "findProject")).contains("known projects");
            return new org.springframework.ai.chat.messages.AssistantMessage("I couldn't find a project called Atlantis.");
        });

        String turn = chat(null, "I joined Atlantis");

        assertThat(JsonPath.<Boolean>read(turn, "$.toolCalls[0].success")).isFalse();
    }

    // --- helpers --------------------------------------------------------------------------------------

    private String analyseInFirstTurn() throws Exception {
        model.thenCall("calculateMissingAccess", "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}");
        model.thenSay("You are missing Jira, Database and VPN. Would you like me to submit the missing access requests?");
        return JsonPath.read(chat(null, JOINED), "$.conversationId");
    }

    private List<Long> missingIds() {
        return List.of("NOVATECH_DB_READ", "NOVATECH_JIRA", "NOVATECH_VPN").stream()
                .map(c -> entitlements.findByEntitlementCode(c).orElseThrow().getId()).toList();
    }

    private String createArgs(Object ids) {
        return "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + ",\"entitlementIds\":" + ids + "}";
    }

    private String chat(String conversationId, String message) throws Exception {
        String body = conversationId == null
                ? "{\"message\":\"" + message + "\"}"
                : "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + message + "\"}";
        return mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String chatSelecting(String conversationId, String message, List<Long> selected) throws Exception {
        String body = "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + message
                + "\",\"selectedEntitlementIds\":" + selected + "}";
        return mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** What a well-behaved LLM would write for Step 6, built only from the tool result. */
    private static String present(Prompt p) {
        String json = ScriptedChatModel.lastToolResult(p, "calculateMissingAccess");
        StringBuilder sb = new StringBuilder("You already have:\n");
        JsonPath.<List<String>>read(json, "$.alreadyHave[*].entitlementName").forEach(n -> sb.append("✓ ").append(n).append('\n'));
        sb.append("\nYou are missing:\n");
        JsonPath.<List<String>>read(json, "$.missing[*].entitlementName").forEach(n -> sb.append("• ").append(n).append('\n'));
        sb.append("\nThese accesses are part of the standard ").append(JsonPath.<String>read(json, "$.projectName"))
                .append(' ').append(JsonPath.<String>read(json, "$.role")).append(" access profile.\n\n")
                .append("Would you like me to submit the missing access requests?");
        return sb.toString();
    }

    /** Step 9 text, built only from the createAccessRequest tool result. */
    private static String confirmation(Prompt p) {
        String json = ScriptedChatModel.lastToolResult(p, "createAccessRequest");
        StringBuilder sb = new StringBuilder("Access request submitted successfully.\n\nRequest ID: ")
                .append(JsonPath.<String>read(json, "$.requestId")).append("\n\nRequested:\n");
        JsonPath.<List<String>>read(json, "$.requested[*].entitlementCode").forEach(c -> sb.append("✓ ").append(c).append('\n'));
        return sb.append("\nStatus:\n").append(JsonPath.<String>read(json, "$.statusLabel")).toString();
    }
}
