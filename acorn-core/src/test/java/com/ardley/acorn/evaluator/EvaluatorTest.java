package com.ardley.acorn.evaluator;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.attribute.Principal;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.policy.EvaluationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the authorization evaluation engine against real permission configurations.
 * Covers both gate checks (no resource) and full resource-level evaluations with
 * scope filtering and isolation policy enforcement.
 */
class EvaluatorTest {

    static final Action UPDATE_USER = action("UpdateUser", "Update a user");
    static final Action DELETE_USER = action("DeleteUser", "Delete a user");
    static final Action LIST_USERS = action("ListUsers", "List users");

    static final EvaluationPolicy TENANT_ISOLATION = EvaluationPolicy.withIsolation("tenant_id");
    static final EvaluationPolicy NO_ISOLATION = EvaluationPolicy.none();

    @Nested
    @DisplayName("Gate checks (canPerformAction)")
    class GateChecks {

        @Test
        @DisplayName("allowAll permission set permits any action")
        void allowAllPermitsAnyAction() {
            PermissionSet perms = PermissionSet.allowAll();

            AuthorizationResult result = Evaluator.canPerformAction(perms, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("Empty permission set denies all actions")
        void emptyDeniesAll() {
            PermissionSet perms = PermissionSet.empty();

            AuthorizationResult result = Evaluator.canPerformAction(perms, UPDATE_USER);

            assertThat(result.isDenied()).isTrue();
            assertThat(result.reason()).contains("UpdateUser");
        }

        @Test
        @DisplayName("Unconditional deny overrides allowAll")
        void unconditionalDenyOverridesAllowAll() {
            PermissionSet perms = PermissionSet.fromJson("""
                {
                    "allow": "all",
                    "deny": {"DeleteUser": "all"}
                }
                """);

            assertThat(Evaluator.canPerformAction(perms, DELETE_USER).isDenied()).isTrue();
            assertThat(Evaluator.canPerformAction(perms, UPDATE_USER).permitted()).isTrue();
        }

        @Test
        @DisplayName("Scoped allow still passes gate check (resource not available yet)")
        void scopedAllowPassesGateCheck() {
            PermissionSet perms = PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """);

            AuthorizationResult result = Evaluator.canPerformAction(perms, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("Action not in allow map is implicitly denied")
        void actionNotInAllowMapDenied() {
            PermissionSet perms = PermissionSet.fromJson("""
                {"allow": {"ListUsers": "all"}}
                """);

            assertThat(Evaluator.canPerformAction(perms, UPDATE_USER).isDenied()).isTrue();
            assertThat(Evaluator.canPerformAction(perms, LIST_USERS).permitted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Full evaluation (evaluate)")
    class FullEvaluation {

        @Test
        @DisplayName("Isolation violation: principal and resource in different tenants")
        void isolationViolationDenies() {
            PermissionSet perms = PermissionSet.allowAll();
            var principal = principalWith("tenant_id", "tenant-A", "department", "Eng");
            var resource = Attributes.builder().with("tenant_id", "tenant-B").build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.isDenied()).isTrue();
            assertThat(result.reason()).contains("Isolation violation");
        }

        @Test
        @DisplayName("Isolation passes when resource lacks the isolation attribute")
        void isolationSkippedWhenResourceLacksAttribute() {
            PermissionSet perms = PermissionSet.allowAll();
            var principal = principalWith("tenant_id", "tenant-A");
            var resource = Attributes.builder().with("status", "active").build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("No isolation policy allows cross-tenant access")
        void noIsolationAllowsCrossTenant() {
            PermissionSet perms = PermissionSet.allowAll();
            var principal = principalWith("tenant_id", "tenant-A");
            var resource = Attributes.builder().with("tenant_id", "tenant-B").build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, NO_ISOLATION, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("Scoped allow matches when principal and resource share department")
        void scopedAllowMatchesSameDepartment() {
            PermissionSet perms = PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """);
            var principal = principalWith("tenant_id", "t-1", "department", "Engineering");
            var resource = Attributes.builder()
                    .with("tenant_id", "t-1")
                    .with("department", "Engineering")
                    .build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("Scoped allow denies when departments differ")
        void scopedAllowDeniesOnDepartmentMismatch() {
            PermissionSet perms = PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}}}}
                """);
            var principal = principalWith("tenant_id", "t-1", "department", "Engineering");
            var resource = Attributes.builder()
                    .with("tenant_id", "t-1")
                    .with("department", "Sales")
                    .build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.isDenied()).isTrue();
            assertThat(result.reason()).contains("scope filter did not match");
        }

        @Test
        @DisplayName("Scoped deny blocks access even when allow is unconditional")
        void scopedDenyBlocksUnconditionalAllow() {
            PermissionSet perms = PermissionSet.fromJson("""
                {
                    "allow": {"UpdateUser": "all"},
                    "deny": {"UpdateUser": {"department": {"equals": "Executive"}}}
                }
                """);
            var principal = principalWith("tenant_id", "t-1");
            var resource = Attributes.builder()
                    .with("tenant_id", "t-1")
                    .with("department", "Executive")
                    .build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.isDenied()).isTrue();
            assertThat(result.reason()).contains("scope matched");
        }

        @Test
        @DisplayName("Scoped deny does not block when scope filter doesn't match")
        void scopedDenyPassesWhenFilterDoesNotMatch() {
            PermissionSet perms = PermissionSet.fromJson("""
                {
                    "allow": {"UpdateUser": "all"},
                    "deny": {"UpdateUser": {"department": {"equals": "Executive"}}}
                }
                """);
            var principal = principalWith("tenant_id", "t-1");
            var resource = Attributes.builder()
                    .with("tenant_id", "t-1")
                    .with("department", "Engineering")
                    .build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);

            assertThat(result.permitted()).isTrue();
        }

        @Test
        @DisplayName("Unconditional deny takes precedence over unconditional allow")
        void denyWinsOverAllow() {
            PermissionSet perms = PermissionSet.fromJson("""
                {
                    "allow": {"DeleteUser": "all"},
                    "deny": {"DeleteUser": "all"}
                }
                """);
            var principal = principalWith("tenant_id", "t-1");
            var resource = Attributes.builder().with("tenant_id", "t-1").build();

            AuthorizationResult result = Evaluator.evaluate(perms, principal, resource, TENANT_ISOLATION, DELETE_USER);

            assertThat(result.isDenied()).isTrue();
        }

        @Test
        @DisplayName("Multi-dimensional scope filter requires ALL dimensions to match")
        void multiDimensionalScopeRequiresAllDimensions() {
            PermissionSet perms = PermissionSet.fromJson("""
                {"allow": {"UpdateUser": {"department": {"match": "principal"}, "status": {"equals": "active"}}}}
                """);
            var principal = principalWith("tenant_id", "t-1", "department", "Eng");

            // Both match
            var activeEng = Attributes.builder()
                    .with("tenant_id", "t-1").with("department", "Eng").with("status", "active").build();
            assertThat(Evaluator.evaluate(perms, principal, activeEng, TENANT_ISOLATION, UPDATE_USER).permitted()).isTrue();

            // Department matches but status doesn't
            var inactiveEng = Attributes.builder()
                    .with("tenant_id", "t-1").with("department", "Eng").with("status", "suspended").build();
            assertThat(Evaluator.evaluate(perms, principal, inactiveEng, TENANT_ISOLATION, UPDATE_USER).isDenied()).isTrue();

            // Status matches but department doesn't
            var activeSales = Attributes.builder()
                    .with("tenant_id", "t-1").with("department", "Sales").with("status", "active").build();
            assertThat(Evaluator.evaluate(perms, principal, activeSales, TENANT_ISOLATION, UPDATE_USER).isDenied()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Attributes principalWith(String... pairs) {
        var builder = Attributes.builder();
        for (int i = 0; i < pairs.length; i += 2) {
            builder.with(pairs[i], pairs[i + 1]);
        }
        return builder.build();
    }

    private static Action action(String name, String description) {
        return new Action() {
            public String name() { return name; }
            public String description() { return description; }
        };
    }
}
