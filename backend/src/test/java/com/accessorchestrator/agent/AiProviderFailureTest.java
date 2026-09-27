package com.accessorchestrator.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * When the AI provider fails, the employee gets a plain explanation and none of the provider's error text
 * (which can include part of the API key, vendor wording and links).
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036")
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.ai.openai.api-key=sk-test-wrong-key")
class AiProviderFailureTest {

    /** What OpenAI answers for a wrong key, as Spring AI reports it. */
    private static final String OPENAI_401 = """
            HTTP 401 - {
                "error": {
                    "message": "Incorrect API key provided: sk-tes*******-key. You can find your API key at https://platform.openai.com/account/api-keys.",
                    "type": "invalid_request_error",
                    "code": "invalid_api_key"
                }
            }""";

    @TestConfiguration
    static class ScriptedModelConfig {
        @Bean
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ScriptedChatModel model;

    @BeforeEach
    void setUp() {
        model.reset();
    }

    @Test
    void wrongApiKeyIsReportedAsAConfigurationProblemWithoutProviderDetails() throws Exception {
        model.then(p -> { throw new NonTransientAiException(OPENAI_401); });

        String body = mvc.perform(chat("Who am I?"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(AiProviderFailures.REJECTED_CREDENTIALS))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("Incorrect API key", "sk-tes", "platform.openai.com", "invalid_api_key",
                "HTTP 401");
    }

    @Test
    void rateLimitAndOutagesAskTheUserToTryAgain() throws Exception {
        model.then(p -> { throw new TransientAiException("HTTP 429 - {\"error\":{\"code\":\"rate_limit_exceeded\"}}"); });
        mvc.perform(chat("Who am I?"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(AiProviderFailures.BUSY));

        model.then(p -> { throw new ResourceAccessException("I/O error on POST request: Read timed out"); });
        mvc.perform(chat("Who am I?"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(AiProviderFailures.UNAVAILABLE));
    }

    @Test
    void classifiesProviderStatuses() {
        assertThat(AiProviderFailures.toUserFacing(new NonTransientAiException(OPENAI_401)))
                .satisfies(e -> assertThat(e.isNotConfigured()).isTrue())
                .hasMessage(AiProviderFailures.REJECTED_CREDENTIALS);
        assertThat(AiProviderFailures.toUserFacing(new NonTransientAiException("HTTP 403 - forbidden")).getMessage())
                .isEqualTo(AiProviderFailures.REJECTED_CREDENTIALS);
        assertThat(AiProviderFailures.toUserFacing(new NonTransientAiException("HTTP 404 - model not found")))
                .satisfies(e -> assertThat(e.isNotConfigured()).isTrue())
                .hasMessage(AiProviderFailures.MISCONFIGURED);
        assertThat(AiProviderFailures.toUserFacing(new NonTransientAiException("HTTP 400 - context too long")))
                .satisfies(e -> assertThat(e.isNotConfigured()).isFalse())
                .hasMessage(AiProviderFailures.BAD_REQUEST);
        assertThat(AiProviderFailures.toUserFacing(new TransientAiException("HTTP 503 - overloaded")).getMessage())
                .isEqualTo(AiProviderFailures.UNAVAILABLE);
        assertThat(AiProviderFailures.toUserFacing(new IllegalStateException("something odd")).getMessage())
                .isEqualTo(AiProviderFailures.UNAVAILABLE);
        // Status carried by a wrapped client exception rather than the message
        RuntimeException wrapped = new RuntimeException("call failed", HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
        assertThat(AiProviderFailures.httpStatus(wrapped)).isEqualTo(401);
    }

    private static org.springframework.test.web.servlet.RequestBuilder chat(String message) {
        return post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}");
    }
}
