package com.accessorchestrator.service;

import com.accessorchestrator.exception.BusinessRuleException;
import com.accessorchestrator.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProjectLookupTest {

    @Autowired
    private ProjectService projectService;

    @ParameterizedTest
    @ValueSource(strings = {"Novatech", "NovaTech", "novatech", "NOVATECH", "Nova Tech", "nova-tech", "nova",
            "the Novatech project"})
    void resolvesUserWordingToNovatech(String query) {
        assertThat(projectService.findByNameOrCode(query).projectCode()).isEqualTo("NOVATECH");
    }

    @Test
    void unknownProjectListsKnownOnes() {
        assertThatThrownBy(() -> projectService.findByNameOrCode("Atlantis"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Novatech").hasMessageContaining("Orion");
    }

    @Test
    void blankIsRejected() {
        assertThatThrownBy(() -> projectService.findByNameOrCode("  ")).isInstanceOf(BusinessRuleException.class);
    }
}
