package com.ardley.acorn.evaluator;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests evaluation scenarios involving multiple actions checked sequentially
 * (simulating @RequiresActions with multiple values) and edge cases around
 * PermissionLevel.None in the allow map.
 */
class EvaluatorMultiActionTest {

    static final EvaluationPolicy POLICY = EvaluationPolicy.withIsolation("tenant_id");

    static final Action LIST_USERS = action("ListUsers");
    static final Action UPDATE_USER = action("UpdateUser");
    static final Action DELETE_USER = action("DeleteUser");

    @Test
    @DisplayName("All actions must pass for multi-action gate check (AND semantics)")
    void multiActionGateAllMustPass() {
        PermissionSet perms = PermissionSet.fromJson("""
            {"allow": {"ListUsers": "all", "UpdateUser": "all"}}
            """);

        // Both pass individually
        assertThat(Evaluator.canPerformAction(perms, LIST_USERS).permitted()).isTrue();
        assertThat(Evaluator.canPerformAction(perms, UPDATE_USER).permitted()).isTrue();

        // Missing action fails the set
        assertThat(Evaluator.canPerformAction(perms, DELETE_USER).isDenied()).isTrue();
    }

    @Test
    @DisplayName("Explicit 'none' level in allow map is treated as denied")
    void explicitNoneInAllowMapDenied() {
        PermissionSet perms = PermissionSet.fromJson("""
            {"allow": {"ListUsers": "all", "DeleteUser": "none"}}
            """);

        // hasAllowFor returns true (key exists) but evaluate should deny
        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("tenant_id", "t-1").build();

        AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, POLICY, DELETE_USER);
        assertThat(result.isDenied()).isTrue();
    }

    @Test
    @DisplayName("Gate check with 'none' level is denied (explicit no-access)")
    void gateCheckWithNoneLevelDenied() {
        PermissionSet perms = PermissionSet.fromJson("""
            {"allow": {"DeleteUser": "none"}}
            """);

        AuthorizationResult result = Evaluator.canPerformAction(perms, DELETE_USER);

        assertThat(result.isDenied()).isTrue();
    }

    @Test
    @DisplayName("Scoped deny on one action doesn't affect other actions")
    void scopedDenyIsolatedToAction() {
        PermissionSet perms = PermissionSet.fromJson("""
            {
                "allow": {"ListUsers": "all", "UpdateUser": "all", "DeleteUser": "all"},
                "deny": {"DeleteUser": {"department": {"equals": "Executive"}}}
            }
            """);

        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var executiveResource = Attributes.builder()
                .with("tenant_id", "t-1")
                .with("department", "Executive")
                .build();

        // DeleteUser denied on Executive resources
        assertThat(Evaluator.evaluate(perms, principal, executiveResource, POLICY, DELETE_USER).isDenied()).isTrue();

        // UpdateUser still allowed on same resource (deny scoped to DeleteUser only)
        assertThat(Evaluator.evaluate(perms, principal, executiveResource, POLICY, UPDATE_USER).permitted()).isTrue();
    }

    @Test
    @DisplayName("allowAll with scoped deny: deny only triggers when scope matches")
    void allowAllWithScopedDenySelectiveDenial() {
        PermissionSet perms = PermissionSet.fromJson("""
            {
                "allow": "all",
                "deny": {"DeleteUser": {"department": {"equals": "Executive"}}}
            }
            """);

        var principal = Attributes.builder().with("tenant_id", "t-1").build();

        var executiveUser = Attributes.builder().with("tenant_id", "t-1").with("department", "Executive").build();
        var engineeringUser = Attributes.builder().with("tenant_id", "t-1").with("department", "Engineering").build();

        assertThat(Evaluator.evaluate(perms, principal, executiveUser, POLICY, DELETE_USER).isDenied()).isTrue();
        assertThat(Evaluator.evaluate(perms, principal, engineeringUser, POLICY, DELETE_USER).permitted()).isTrue();
    }

    private static Action action(String name) {
        return new Action() {
            public String name() { return name; }
            public String description() { return "test"; }
        };
    }
}
