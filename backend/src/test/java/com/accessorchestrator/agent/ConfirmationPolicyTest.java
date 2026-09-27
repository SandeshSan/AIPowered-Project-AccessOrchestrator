package com.accessorchestrator.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Yes", "yes please", "Yeah, go ahead", "ok", "Sure!", "Please submit them",
            "Confirm", "yep do it", "YES", "sounds good, submit"})
    void affirmative(String message) {
        assertThat(ConfirmationPolicy.isAffirmative(message)).isTrue();
        assertThat(ConfirmationPolicy.isNegative(message)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"No", "no thanks", "Don't submit yet", "don’t", "not now", "cancel",
            "yes... actually no", "wait", "Hold on"})
    void negative(String message) {
        assertThat(ConfirmationPolicy.isAffirmative(message)).isFalse();
        assertThat(ConfirmationPolicy.isNegative(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"What is the VPN for?", "Who approves this?", "", "I joined Novatech",
            "tell me more about Jira"})
    void unclear(String message) {
        assertThat(ConfirmationPolicy.isAffirmative(message)).isFalse();
    }
}
