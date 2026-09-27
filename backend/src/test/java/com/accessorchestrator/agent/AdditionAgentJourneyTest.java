package com.accessorchestrator.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Adding reportees to projects through the assistant, with a scripted model and a stubbed IGA. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdditionAgentJourneyTest {

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
    @MockitoBean private IgaProvider iga;

    private long atlasId;

    @BeforeEach
    void setUp() {
        atlasId = projects.findByProjectCodeIgnoreCase("ATLAS").orElseThrow().getId();
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-30001", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void managerAddsAReporteeAfterPreviewAndConfirmation() throws Exception {
        model.then(p -> {
            assertThat(systemText(p)).contains("add them to projects").contains("may view their access");
            return model.calls(new String[][]{{"findEmployee", "{\"query\":\"John\"}"},
                    {"findProject", "{\"projectName\":\"Atlas\"}"}});
        });
        model.then(p -> model.calls(new String[][]{{"previewAddToProject", "{\"userId\":\""
                + ScriptedChatModel.read(p, "findEmployee", "$[0].userId") + "\",\"projectId\":"
                + ScriptedChatModel.read(p, "findProject", "$.projectId") + "}"}}));
        model.thenSay("Adding John to Atlas Payments; he will still need 6 entitlements. Shall I go ahead?");
        String turn1 = chat("NT10020", null, "Add John to the Atlas project", false);

        assertThat(JsonPath.<String>read(turn1, "$.pendingAction")).isEqualTo("PROJECT_ADDITION");
        assertThat(JsonPath.<List<String>>read(turn1, "$.additionPreview.missingAccess[*].entitlementCode")).hasSize(6);
        verify(iga, never()).createAccessRequest(any());

        String conversationId = JsonPath.read(turn1, "$.conversationId");
        model.then(p -> {
            Matcher m = Pattern.compile("addEmployeeToProject with userId=(\\w+), projectId=(\\d+)").matcher(systemText(p));
            assertThat(m.find()).isTrue();
            return model.calls(new String[][]{{"addEmployeeToProject",
                    "{\"userId\":\"" + m.group(1) + "\",\"projectId\":" + m.group(2) + "}"}});
        });
        model.thenSay("John is now a member of Atlas Payments. He can request the 6 entitlements he needs.");
        String turn2 = chat("NT10020", conversationId, "Yes, add him", false);

        assertThat(JsonPath.<String>read(turn2, "$.addition.projectName")).isEqualTo("Atlas Payments");
        assertThat(JsonPath.<List<String>>read(turn2, "$.addition.missingAccess[*].entitlementCode")).hasSize(6);
        assertThat(JsonPath.<Object>read(turn2, "$.pendingAction")).isNull();
        verify(iga, never()).createAccessRequest(any()); // membership only
    }

    @Test
    void additionInTheSameTurnAsThePreviewIsRefused() throws Exception {
        model.thenCall("previewAddToProject", "{\"userId\":\"NT10036\",\"projectId\":" + atlasId + "}");
        model.thenCall("addEmployeeToProject", "{\"userId\":\"NT10036\",\"projectId\":" + atlasId + "}");
        model.thenSay("Shall I go ahead?");

        String turn = chat("NT10020", null, "Add John to Atlas now", false);

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[1].error")).contains("confirmation required");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void confirmButtonAddsWithoutWords() throws Exception {
        model.thenCall("previewAddToProject", "{\"userId\":\"NT10036\",\"projectId\":" + atlasId + "}");
        model.thenSay("Shall I go ahead?");
        String conversationId = JsonPath.read(chat("NT10020", null, "Add John to Atlas", false), "$.conversationId");

        model.thenCall("addEmployeeToProject", "{\"userId\":\"NT10036\",\"projectId\":" + atlasId + "}");
        model.thenSay("Done.");
        String turn2 = chat("NT10020", conversationId, "Add John to Atlas Payments", true);

        assertThat(JsonPath.<String>read(turn2, "$.addition.userId")).isEqualTo("NT10036");
    }

    /** Regression: "move Elena from Orion to Novatech" previews a removal and an addition in one turn. */
    @Test
    void movingAReportBetweenProjectsConfirmsBothActions() throws Exception {
        long orion = projects.findByProjectCodeIgnoreCase("ORION").orElseThrow().getId();
        long novatech = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        model.then(p -> model.calls(new String[][]{
                {"previewProjectRemoval", "{\"userId\":\"NT10063\",\"projectId\":" + orion + "}"},
                {"previewAddToProject", "{\"userId\":\"NT10063\",\"projectId\":" + novatech + "}"}}));
        model.thenSay("Moving Elena: please confirm and give a reason.");
        String turn1 = chat("NT10020", null, "Move Elena from Orion to Novatech", false);

        assertThat(JsonPath.<List<String>>read(turn1, "$.pendingActions"))
                .containsExactly("PROJECT_REMOVAL", "PROJECT_ADDITION");

        String conversationId = JsonPath.read(turn1, "$.conversationId");
        model.then(p -> {
            assertThat(systemText(p)).contains("call removeEmployeeFromProject").contains("call addEmployeeToProject");
            return model.calls(new String[][]{
                    {"removeEmployeeFromProject",
                            "{\"userId\":\"NT10063\",\"projectId\":" + orion + ",\"reason\":\"Moved to Novatech\"}"},
                    {"addEmployeeToProject", "{\"userId\":\"NT10063\",\"projectId\":" + novatech + "}"}});
        });
        model.thenSay("Elena has moved from Orion to Novatech.");
        String turn2 = chat("NT10020", conversationId, "Yes, she moved to Novatech", false);

        assertThat(JsonPath.<List<Boolean>>read(turn2, "$.toolCalls[*].success")).containsOnly(true);
        assertThat(JsonPath.<String>read(turn2, "$.removal.projectName")).isEqualTo("Orion");
        assertThat(JsonPath.<String>read(turn2, "$.addition.projectName")).isEqualTo("Novatech");
        assertThat(JsonPath.<List<String>>read(turn2, "$.pendingActions")).isEmpty();
    }

    /** Regression: on "yes" the model called createAccessRequest for someone else instead of adding them. */
    @Test
    void wrongToolOnConfirmationIsRefusedWithAHintAndCorrected() throws Exception {
        long novatech = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        model.thenCall("previewAddToProject", "{\"userId\":\"NT10063\",\"projectId\":" + novatech + "}");
        model.thenSay("Shall I add Elena to Novatech?");
        String conversationId = JsonPath.read(chat("NT10020", null, "Add Elena to Novatech", false), "$.conversationId");

        model.thenCall("createAccessRequest",
                "{\"userId\":\"NT10063\",\"projectId\":" + novatech + ",\"entitlementIds\":[1]}");
        model.then(p -> {
            assertThat(ScriptedChatModel.lastToolResult(p, "createAccessRequest")).contains("addEmployeeToProject");
            return new org.springframework.ai.chat.messages.AssistantMessage("Something went wrong.");
        });
        // retry after the system correction
        model.thenCall("addEmployeeToProject", "{\"userId\":\"NT10063\",\"projectId\":" + novatech + "}");
        model.thenSay("Elena is now a member of Novatech.");
        String turn2 = chat("NT10020", conversationId, "Yes", false);

        assertThat(JsonPath.<String>read(turn2, "$.addition.projectName")).isEqualTo("Novatech");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void aLaterPreviewReplacesOlderPendingActions() throws Exception {
        model.thenCall("previewAddToProject", "{\"userId\":\"NT10036\",\"projectId\":" + atlasId + "}");
        model.thenSay("Shall I add John to Atlas?");
        String conversationId = JsonPath.read(chat("NT10020", null, "Add John to Atlas", false), "$.conversationId");

        long novatech = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatech + "}");
        model.thenSay("Please confirm removing Asha and give a reason.");
        String turn2 = chat("NT10020", conversationId, "Actually, remove Asha from Novatech instead", false);

        assertThat(JsonPath.<List<String>>read(turn2, "$.pendingActions")).containsExactly("PROJECT_REMOVAL");
    }

    @Test
    void managerCanViewAReporteesAccessButEmployeesCannot() throws Exception {
        model.thenCall("getUserExistingAccess", "{\"userId\":\"NT10042\"}");
        model.then(p -> {
            assertThat(ScriptedChatModel.lastToolResult(p, "getUserExistingAccess")).contains("NOVATECH_DEV");
            return new org.springframework.ai.chat.messages.AssistantMessage("Asha has 4 active entitlements.");
        });
        String mei = chat("NT10020", null, "What access does Asha have?", false);
        assertThat(JsonPath.<Boolean>read(mei, "$.toolCalls[0].success")).isTrue();

        model.thenCall("getUserExistingAccess", "{\"userId\":\"NT10042\"}");
        model.thenSay("You can only view your own access.");
        String john = chat("NT10036", null, "What access does Asha have?", false);
        assertThat(JsonPath.<String>read(john, "$.toolCalls[0].error")).contains("does not report to John");
    }

    @Test
    void anyoneCanCompareTheirAccessWithAColleague() throws Exception {
        model.thenCall("findEmployee", "{\"query\":\"Asha\"}");
        model.thenCall("compareAccessWithColleague", "{\"colleagueUserId\":\"NT10042\"}");
        model.then(p -> {
            String result = ScriptedChatModel.lastToolResult(p, "compareAccessWithColleague");
            assertThat(JsonPath.<List<String>>read(result, "$.colleagueHasYouDont[*].entitlementCode"))
                    .containsExactly("ORION_DEV", "NOVATECH_JIRA");
            assertThat(JsonPath.<List<String>>read(result,
                    "$.colleagueHasYouDont[?(@.entitlementCode == 'NOVATECH_JIRA')].requestableForProject"))
                    .containsExactly("Novatech");
            assertThat(JsonPath.<String>read(result, "$.nextStep")).contains("calculateMissingAccess");
            return new org.springframework.ai.chat.messages.AssistantMessage("Asha has Orion GitHub and Novatech Jira.");
        });

        String john = chat("NT10036", null, "What access does Asha have that I don't?", false);

        assertThat(JsonPath.<Boolean>read(john, "$.toolCalls[1].success")).isTrue();
        assertThat(JsonPath.<Object>read(john, "$.pendingAction")).isNull(); // comparing requests nothing
    }

    @Test
    void restrictedItemsReachTheModelLabelledButUnnamed() throws Exception {
        model.thenCall("compareAccessWithColleague", "{\"colleagueUserId\":\"NT10051\"}");
        model.then(p -> {
            String result = ScriptedChatModel.lastToolResult(p, "compareAccessWithColleague");
            assertThat(result).doesNotContain("ATLAS_VAULT_READ").doesNotContain("Vault Secrets Reader");
            assertThat(JsonPath.<List<String>>read(result, "$.colleagueHasYouDont[?(@.restricted == true)].entitlementName"))
                    .containsExactly("Restricted (high-risk access)");
            return new org.springframework.ai.chat.messages.AssistantMessage("Priya has one restricted item.");
        });

        chat("NT10036", null, "Compare my access with Priya's", false);
    }

    @Test
    void theViewRefusalPointsToTheComparison() throws Exception {
        model.thenCall("getUserExistingAccess", "{\"userId\":\"NT10042\"}");
        model.thenSay("You can compare your access with hers instead.");

        String john = chat("NT10036", null, "What access does Asha have?", false);

        assertThat(JsonPath.<String>read(john, "$.toolCalls[0].error")).contains("compareAccessWithColleague");
    }

    @Test
    void employeeCannotAddPeople() throws Exception {
        model.thenCall("previewAddToProject", "{\"userId\":\"NT10042\",\"projectId\":" + atlasId + "}");
        model.thenSay("Only a manager can add people.");

        String turn = chat("NT10036", null, "Add Asha to Atlas", false);

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[0].error")).contains("does not report to you");
        assertThat(JsonPath.<Object>read(turn, "$.pendingAction")).isNull();
    }

    private String chat(String userId, String conversationId, String message, boolean confirmAddition) throws Exception {
        String body = "{" + (conversationId == null ? "" : "\"conversationId\":\"" + conversationId + "\",")
                + "\"message\":\"" + message + "\"" + (confirmAddition ? ",\"confirmAddition\":true" : "") + "}";
        return mvc.perform(post("/api/agent/chat").with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String systemText(Prompt p) {
        return p.getInstructions().stream().filter(m -> m instanceof SystemMessage)
                .map(m -> m.getText()).findFirst().orElseThrow();
    }
}
