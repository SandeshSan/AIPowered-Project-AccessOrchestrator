package com.accessorchestrator.service;

import com.accessorchestrator.domain.User;
import com.accessorchestrator.domain.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reporting-line rules: up to three levels, active managers only, no loops. */
class AccessAuthorityTest {

    private final User ceo = user("U1");
    private final User director = user("U2");
    private final User manager = user("U3");
    private final User lead = user("U4");
    private final User engineer = user("U5");

    {
        director.setManager(ceo);
        manager.setManager(director);
        lead.setManager(manager);
        engineer.setManager(lead);
    }

    @Test
    void levelsUpToThree() {
        assertThat(AccessAuthority.managementLevel(lead, engineer)).contains(1);
        assertThat(AccessAuthority.managementLevel(manager, engineer)).contains(2);
        assertThat(AccessAuthority.managementLevel(director, engineer)).contains(3);
    }

    @Test
    void fourLevelsUpHasNoAuthority() {
        assertThat(AccessAuthority.managementLevel(ceo, engineer)).isEmpty();
        assertThat(AccessAuthority.managementLevel(ceo, lead)).contains(3);
    }

    @Test
    void reportsHaveNoAuthorityOverManagers() {
        assertThat(AccessAuthority.managementLevel(engineer, lead)).isEmpty();
        assertThat(AccessAuthority.managementLevel(engineer, engineer)).isEmpty();
    }

    @Test
    void inactiveManagersHaveNoAuthority() {
        lead.setStatus(UserStatus.INACTIVE);
        assertThat(AccessAuthority.managementLevel(lead, engineer)).isEmpty();
    }

    @Test
    void loopsAndSelfManagementAreRejected() {
        assertThatThrownBy(() -> ceo.setManager(engineer)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loop");
        assertThatThrownBy(() -> lead.setManager(lead)).isInstanceOf(IllegalArgumentException.class);
    }

    private static User user(String id) {
        return new User(id, "Name " + id, id + "@example.com", "Role", "Dept", UserStatus.ACTIVE);
    }
}
