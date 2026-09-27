package com.accessorchestrator.agent;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import com.accessorchestrator.domain.RequestStatus;
import com.accessorchestrator.domain.RequestType;
import com.accessorchestrator.iga.AccessRequestResponse;
import com.accessorchestrator.iga.IgaAccessRequest;
import com.accessorchestrator.iga.IgaProvider;
import com.accessorchestrator.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Removing people from projects through the assistant, with a scripted model and a stubbed IGA. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RemovalAgentJourneyTest {

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

    private long novatechId;

    @BeforeEach
    void setUp() {
        novatechId = projects.findByProjectCodeIgnoreCase("NOVATECH").orElseThrow().getId();
        when(iga.name()).thenReturn("stub");
        when(iga.createAccessRequest(any()))
                .thenReturn(new AccessRequestResponse("REQ-20001", RequestStatus.PENDING_APPROVAL, "ok"));
    }

    @Test
    void managerRemovesAnEmployeeAfterPreviewAndConfirmation() throws Exception {
        model.then(p -> {
            assertThat(systemText(p)).contains("line manager of [").contains("Asha (NT10042)")
                    .contains("David (NT10070) via Priya");
            return model.calls(new String[][]{{"findEmployee", "{\"query\":\"Asha\"}"},
                    {"findProject", "{\"projectName\":\"Novatech\"}"}});
        });
        model.then(p -> model.calls(new String[][]{{"previewProjectRemoval", "{\"userId\":\""
                + ScriptedChatModel.read(p, "findEmployee", "$[0].userId") + "\",\"projectId\":"
                + ScriptedChatModel.read(p, "findProject", "$.projectId") + "}"}}));
        model.thenSay("Removing Asha from Novatech will revoke 2 entitlements. Please confirm and give a reason.");
        String turn1 = chat("NT10020", null, "Remove Asha from Novatech");

        assertThat(JsonPath.<String>read(turn1, "$.pendingAction")).isEqualTo("PROJECT_REMOVAL");
        assertThat(JsonPath.<List<String>>read(turn1, "$.removalPreview.toRevoke[*].entitlementCode"))
                .containsExactly("GCP_NOVATECH_DEV", "NOVATECH_DEV", "NOVATECH_JIRA");
        assertThat(JsonPath.<List<String>>read(turn1, "$.removalPreview.keptDefault[*].entitlementCode")).isEmpty();
        verify(iga, never()).createAccessRequest(any());

        String conversationId = JsonPath.read(turn1, "$.conversationId");
        model.then(p -> {
            Matcher m = Pattern.compile("removeEmployeeFromProject with userId=(\\w+), projectId=(\\d+)")
                    .matcher(systemText(p));
            assertThat(m.find()).as("session context carries the pending removal").isTrue();
            return model.calls(new String[][]{{"removeEmployeeFromProject", "{\"userId\":\"" + m.group(1)
                    + "\",\"projectId\":" + m.group(2) + ",\"reason\":\"Moved to Helios Mobile\"}"}});
        });
        model.thenSay("Asha's removal from Novatech is in progress. Revocation REQ-20001 is pending approval.");
        String turn2 = chat("NT10020", conversationId, "Yes, go ahead. She moved to Helios Mobile.");

        assertThat(JsonPath.<String>read(turn2, "$.removal.revocationRequestId")).isEqualTo("REQ-20001");
        assertThat(JsonPath.<String>read(turn2, "$.removal.revocationStatusLabel")).isEqualTo("Pending Approval");
        assertThat(JsonPath.<String>read(turn2, "$.removal.reason")).isEqualTo("Moved to Helios Mobile");
        assertThat(JsonPath.<Object>read(turn2, "$.pendingAction")).isNull();
        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(iga).createAccessRequest(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(RequestType.REVOKE);
        assertThat(sent.getValue().userId()).isEqualTo("NT10042");
        assertThat(sent.getValue().requestedBy()).isEqualTo("NT10020");
    }

    @Test
    void removalInTheSameTurnAsThePreviewIsRefused() throws Exception {
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenCall("removeEmployeeFromProject",
                "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + ",\"reason\":\"Moved\"}");
        model.thenSay("Please confirm the removal and tell me the reason.");

        String turn = chat("NT10020", null, "Remove Asha from Novatech right away, she moved");

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[1].error")).contains("confirmation required");
        assertThat(JsonPath.<String>read(turn, "$.pendingAction")).isEqualTo("PROJECT_REMOVAL");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void plainEmployeeCannotRemoveSomeoneElse() throws Exception {
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenSay("You can only remove yourself from projects.");

        String turn = chat("NT10036", null, "Remove Asha from Novatech");

        assertThat(JsonPath.<String>read(turn, "$.toolCalls[0].error")).contains("You can remove yourself");
        assertThat(JsonPath.<Object>read(turn, "$.pendingAction")).isNull();
    }

    @Test
    void plainEmployeeCanRemoveThemselves() throws Exception {
        model.then(p -> {
            assertThat(systemText(p)).contains("may not remove other people");
            return model.calls(new String[][]{{"previewProjectRemoval",
                    "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + "}"}});
        });
        model.thenSay("Leaving Novatech will revoke 2 entitlements. Please confirm and give a reason.");
        String conversationId = JsonPath.read(chat("NT10036", null, "I'm leaving Novatech, remove my access"),
                "$.conversationId");

        model.thenCall("removeEmployeeFromProject",
                "{\"userId\":\"NT10036\",\"projectId\":" + novatechId + ",\"reason\":\"Rolled off the project\"}");
        model.thenSay("Done: revocation REQ-20001 is pending approval.");
        String turn2 = chat("NT10036", conversationId, "Yes please, I rolled off the project");

        assertThat(JsonPath.<String>read(turn2, "$.removal.revocationRequestId")).isEqualTo("REQ-20001");
    }

    @Test
    void confirmationWithoutAReasonIsRefused() throws Exception {
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenSay("Please confirm and give a reason.");
        String conversationId = JsonPath.read(chat("NT10020", null, "Remove Asha from Novatech"), "$.conversationId");

        model.thenCall("removeEmployeeFromProject",
                "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + ",\"reason\":\"\"}");
        model.thenSay("What is the reason for removing Asha?");
        String turn2 = chat("NT10020", conversationId, "Yes");

        assertThat(JsonPath.<String>read(turn2, "$.toolCalls[0].error")).contains("a reason is required");
        assertThat(JsonPath.<String>read(turn2, "$.pendingAction")).isEqualTo("PROJECT_REMOVAL");
        verify(iga, never()).createAccessRequest(any());
    }

    @Test
    void fabricatedRemovalIsCorrectedByCallingTheTool() throws Exception {
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenSay("Please confirm and give a reason.");
        String conversationId = JsonPath.read(chat("NT10020", null, "Remove Asha from Novatech"), "$.conversationId");

        model.thenSay("Asha has been removed from Novatech.");
        model.thenCall("removeEmployeeFromProject",
                "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + ",\"reason\":\"Moved to Helios\"}");
        model.thenSay("Removal submitted: revocation REQ-20001 is pending approval.");
        String turn2 = chat("NT10020", conversationId, "Yes, she moved to Helios");

        assertThat(JsonPath.<String>read(turn2, "$.removal.revocationRequestId")).isEqualTo("REQ-20001");
        assertThat(JsonPath.<String>read(turn2, "$.reply")).doesNotContain("has been removed");
    }

    /** The UI's confirm button sends the reason separately; a reason containing "no" must not cancel. */
    @Test
    void confirmButtonWithReasonConfirmsEvenIfTheReasonSaysNo() throws Exception {
        model.thenCall("previewProjectRemoval", "{\"userId\":\"NT10042\",\"projectId\":" + novatechId + "}");
        model.thenSay("Please confirm and give a reason.");
        String conversationId = JsonPath.read(chat("NT10020", null, "Remove Asha from Novatech"), "$.conversationId");

        model.then(p -> {
            assertThat(systemText(p)).contains("CONFIRMED this removal with the confirm button")
                    .contains("\"No longer on the Novatech team\"");
            // the model paraphrases the reason; the typed reason still wins
            return model.calls(new String[][]{{"removeEmployeeFromProject", "{\"userId\":\"NT10042\",\"projectId\":"
                    + novatechId + ",\"reason\":\"left team\"}"}});
        });
        model.thenSay("Removal submitted.");
        String body = "{\"conversationId\":\"" + conversationId + "\",\"message\":\"Yes, remove Asha from Novatech. "
                + "Reason: No longer on the Novatech team\",\"removalReason\":\"No longer on the Novatech team\"}";
        String turn2 = mvc.perform(post("/api/agent/chat").with(user("NT10020"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<String>read(turn2, "$.removal.reason")).isEqualTo("No longer on the Novatech team");
        ArgumentCaptor<IgaAccessRequest> sent = ArgumentCaptor.forClass(IgaAccessRequest.class);
        verify(iga).createAccessRequest(sent.capture());
        assertThat(sent.getValue().justification()).isEqualTo("No longer on the Novatech team");
    }

    @Test
    void confirmButtonWithoutAPendingRemovalIsRejected() throws Exception {
        mvc.perform(post("/api/agent/chat").with(user("NT10020")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Yes\",\"removalReason\":\"Moved on\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String chat(String userId, String conversationId, String message) throws Exception {
        String body = conversationId == null
                ? "{\"message\":\"" + message + "\"}"
                : "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + message + "\"}";
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
