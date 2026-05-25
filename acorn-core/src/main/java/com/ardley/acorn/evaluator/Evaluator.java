package com.ardley.acorn.evaluator;

import com.ardley.acorn.action.Action;
import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.permission.PermissionLevel;
import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.permission.ScopeFilter;
import com.ardley.acorn.policy.EvaluationPolicy;

/**
 * Stateless authorization decision engine.
 *
 * <p>All methods are pure functions — they accept inputs and return a decision with no
 * side effects. The evaluator never performs I/O, never interprets attribute semantics,
 * and carries no mutable state.
 *
 * <p>Two evaluation modes are supported:
 * <ul>
 *   <li>{@link #canPerformAction} — gate check without a resource (for list endpoints,
 *       UI visibility decisions, or any operation where no resource ID is available)</li>
 *   <li>{@link #evaluate} — full evaluation against a specific resource with isolation
 *       enforcement and scope filter matching</li>
 * </ul>
 *
 * <h3>Evaluation Order (deny-wins)</h3>
 * <ol>
 *   <li>Policy isolation check (configurable via {@link EvaluationPolicy})</li>
 *   <li>Unconditional deny (action denied at all scope)</li>
 *   <li>Scoped deny (deny filter matches the resource)</li>
 *   <li>Allow all (superadmin flag on the permission set)</li>
 *   <li>Unconditional allow (action allowed at all scope)</li>
 *   <li>Scoped allow (allow filter matches the resource)</li>
 *   <li>Implicit deny (action not present in the allow map)</li>
 * </ol>
 */
public final class Evaluator {

    private Evaluator() {}

    /**
     * Gate check: determines whether the permission set permits the given action
     * without evaluating against a specific resource.
     *
     * @param permissions the principal's permission set
     * @param action the action to check
     * @return the authorization decision
     */
    public static AuthorizationResult canPerformAction(PermissionSet permissions, Action action) {
        String name = action.name();

        if (permissions.hasUnconditionalDeny(name)) {
            return AuthorizationResult.denied(
                    String.format("Action \"%s\" is explicitly denied", name));
        }

        if (permissions.hasAllowFor(name)) {
            return AuthorizationResult.allowed();
        }

        return AuthorizationResult.denied(
                String.format("Action \"%s\" is not permitted for this role", name));
    }

    /**
     * Full evaluation: determines whether the principal can perform the action on
     * the specified resource, enforcing isolation policy and scope filters.
     *
     * @param permissions the principal's permission set
     * @param principal the requesting principal's attributes
     * @param resource the target resource's attributes
     * @param policy the evaluation policy governing isolation rules
     * @param action the action being performed
     * @return the authorization decision
     */
    public static AuthorizationResult evaluate(
            PermissionSet permissions,
            AttributeSource principal,
            AttributeSource resource,
            EvaluationPolicy policy,
            Action action) {

        String name = action.name();

        // 1. Isolation
        var violation = policy.checkIsolation(principal, resource);
        if (violation.isPresent()) {
            return AuthorizationResult.denied(violation.get());
        }

        // 2. Unconditional deny
        if (permissions.hasUnconditionalDeny(name)) {
            return AuthorizationResult.denied(
                    String.format("Action \"%s\" is unconditionally denied", name));
        }

        // 3. Scoped deny
        var denyLevel = permissions.denyLevel(name);
        if (denyLevel.isPresent() && scopeMatches(denyLevel.get(), principal, resource)) {
            return AuthorizationResult.denied(
                    String.format("Action \"%s\" denied (scope matched)", name));
        }

        // 4. Allow all
        if (permissions.isAllowAll()) {
            return AuthorizationResult.allowed();
        }

        // 5-6. Allow
        var allowLevel = permissions.allowLevel(name);
        if (allowLevel.isEmpty()) {
            return AuthorizationResult.denied(
                    String.format("Action \"%s\" is not permitted for this role", name));
        }

        PermissionLevel level = allowLevel.get();
        if (level.isAll()) {
            return AuthorizationResult.allowed();
        }
        if (level.isScoped()) {
            ScopeFilter filter = level.scopeFilter().orElseThrow();
            if (filter.matches(principal, resource)) {
                return AuthorizationResult.allowed();
            }
            return AuthorizationResult.denied(
                    String.format("Action \"%s\" scope filter did not match", name));
        }

        return AuthorizationResult.denied(
                String.format("Action \"%s\" is not permitted for this role", name));
    }

    private static boolean scopeMatches(PermissionLevel level, AttributeSource principal, AttributeSource resource) {
        if (level.isAll()) return true;
        if (level.isScoped()) {
            return level.scopeFilter().orElseThrow().matches(principal, resource);
        }
        return false;
    }
}
