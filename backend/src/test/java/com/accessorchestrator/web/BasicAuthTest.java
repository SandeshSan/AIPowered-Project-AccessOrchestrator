package com.accessorchestrator.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accessorchestrator.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Real HTTP Basic sign-in against the seeded users (no mocked principal). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BasicAuthTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Value("${app.security.demo-password}") private String demoPassword;

    @Test
    void signedInUserIsTheBasicAuthPrincipal() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("NT10020", demoPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NT10020"))
                .andExpect(jsonPath("$.name").value("Mei"));
    }

    @Test
    void userIdIsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("nt10036", demoPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("NT10036"));
    }

    @Test
    void apiRequiresSignIn() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                // no browser login pop-up: the app shows its own login page
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(post("/api/agent/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordOrUnknownUserIsRejected() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("NT10036", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(httpBasic("NOPE", demoPassword))).andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveUsersCannotSignIn() throws Exception {
        mvc.perform(get("/api/me").with(httpBasic("NT10070", demoPassword))).andExpect(status().isUnauthorized());
    }

    @Test
    void theOldIdentityHeaderIsIgnored() throws Exception {
        mvc.perform(get("/api/me").header("X-User-Id", "NT10001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("X-User-Id", "NT10001").with(httpBasic("NT10036", demoPassword)))
                .andExpect(jsonPath("$.userId").value("NT10036"));
    }

    @Test
    void passwordsAreStoredHashed() {
        assertThat(users.findByUserIdIgnoreCase("NT10036").orElseThrow().getPasswordHash())
                .startsWith("{bcrypt}").doesNotContain(demoPassword);
    }

    @Test
    void healthStaysOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
