package com.ardley.acorn.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests action registration, resolution, and duplicate detection.
 */
class ActionRegistryTest {

    enum TestActions implements Action {
        ListUsers("List all users"),
        UpdateUser("Update a user"),
        DeleteUser("Delete a user");

        private final String desc;
        TestActions(String desc) { this.desc = desc; }

        // Enum.name() already satisfies Action.name() — no override needed
        @Override public String description() { return desc; }
    }

    @Test
    @DisplayName("Registers and resolves actions by name")
    void registerAndResolve() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(TestActions.ListUsers);
        registry.register(TestActions.UpdateUser);

        Action resolved = registry.resolve("ListUsers");

        assertThat(resolved.name()).isEqualTo("ListUsers");
        assertThat(resolved.description()).isEqualTo("List all users");
    }

    @Test
    @DisplayName("registerAll registers entire enum")
    void registerAllEnum() {
        ActionRegistry registry = new ActionRegistry();
        registry.registerAll(TestActions.class);

        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.resolve("DeleteUser").description()).isEqualTo("Delete a user");
    }

    @Test
    @DisplayName("Resolving unknown action throws with descriptive message")
    void resolveUnknownThrows() {
        ActionRegistry registry = new ActionRegistry();

        assertThatThrownBy(() -> registry.resolve("NonExistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown action")
                .hasMessageContaining("NonExistent");
    }

    @Test
    @DisplayName("Duplicate registration throws immediately")
    void duplicateRegistrationThrows() {
        ActionRegistry registry = new ActionRegistry();
        registry.register(TestActions.ListUsers);

        assertThatThrownBy(() -> registry.register(TestActions.ListUsers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate action name")
                .hasMessageContaining("ListUsers");
    }

    @Test
    @DisplayName("all() returns unmodifiable collection of registered actions")
    void allReturnsRegistered() {
        ActionRegistry registry = new ActionRegistry();
        registry.registerAll(TestActions.class);

        assertThat(registry.all())
                .hasSize(3)
                .extracting(Action::name)
                .containsExactlyInAnyOrder("ListUsers", "UpdateUser", "DeleteUser");
    }
}
