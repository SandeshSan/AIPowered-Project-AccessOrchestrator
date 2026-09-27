package com.accessorchestrator.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.security.test.context.support.WithMockUser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The project catalog (/api/projects/**) is for administrators only. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "NT10036") // John, unless a request says otherwise
@ActiveProfiles("test")
class AdminAccessTest {

    private static final String[] CATALOG = {"/api/projects", "/api/projects/1", "/api/projects/1/access",
            "/api/projects/1/required-access"};

    @Autowired
    private MockMvc mvc;

    @Test
    void adminCanBrowseTheCatalog() throws Exception {
        for (String url : CATALOG) {
            mvc.perform(get(url).with(user("NT10001"))).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NT10036", "NT10042", "NT10051"})
    void employeesAreForbidden(String userId) throws Exception {
        for (String url : CATALOG) {
            mvc.perform(get(url).with(user(userId)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("This area is available to administrators only"));
        }
    }

    @Test
    void defaultDemoUserIsNotAnAdmin() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isForbidden());
    }

    @Test
    void userEndpointsStayAvailableToEmployees() throws Exception {
        mvc.perform(get("/api/users/NT10036/dashboard").with(user("NT10036"))).andExpect(status().isOk());
        mvc.perform(get("/api/users/NT10036/projects").with(user("NT10036"))).andExpect(status().isOk());
    }

    @Test
    void meExposesTheAdminFlag() throws Exception {
        mvc.perform(get("/api/me").with(user("NT10001"))).andExpect(jsonPath("$.admin").value(true));
        mvc.perform(get("/api/me").with(user("NT10036"))).andExpect(jsonPath("$.admin").value(false));
    }
}
