package com.ardley.acorn.role;

import com.ardley.acorn.permission.PermissionLevel;
import com.ardley.acorn.permission.PermissionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates role permission configurations for structural correctness and policy compliance.
 *
 * <p>This validator ensures that permission JSON is well-formed and that permission
 * hierarchies are respected. It is intended to be called before persisting role
 * configuration changes.
 *
 * <p>Built-in validation rules:
 * <ul>
 *   <li>Permission JSON must parse without errors</li>
 *   <li>Optional hierarchy enforcement: edit-level access implies view-level access</li>
 * </ul>
 *
 * <p>Unknown action names in the permission JSON are intentionally not flagged here.
 * The evaluator treats unknown actions as implicitly denied — they carry no security
 * risk and are best surfaced by application-level UI validation, not the authorization core.
 */
public final class RoleConfigurationValidator {

    private final List<HierarchyRule> hierarchyRules;

    private RoleConfigurationValidator(List<HierarchyRule> hierarchyRules) {
        this.hierarchyRules = List.copyOf(hierarchyRules);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates a role configuration's permission JSON.
     *
     * @param permissionJson the raw JSON string to validate
     * @return a result containing any validation errors found
     */
    public ValidationResult validate(String permissionJson) {
        List<String> errors = new ArrayList<>();

        PermissionSet permissionSet;
        try {
            permissionSet = PermissionSet.fromJson(permissionJson);
        } catch (IllegalArgumentException e) {
            errors.add("Invalid permission JSON: " + e.getMessage());
            return new ValidationResult(errors);
        }

        for (HierarchyRule rule : hierarchyRules) {
            rule.validate(permissionSet, errors);
        }

        return new ValidationResult(errors);
    }

    /**
     * The result of a validation pass.
     */
    public record ValidationResult(List<String> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /**
     * Defines a hierarchy constraint between two actions.
     * If the "higher" action is granted, the "lower" action must also be granted
     * at an equal or broader level.
     *
     * <p>Example: if "UpdateUser" is granted at "all", then "ViewUser" must
     * also be granted at "all" (edit implies view).
     */
    public record HierarchyRule(String higherAction, String lowerAction) {

        void validate(PermissionSet permissionSet, List<String> errors) {
            Optional<PermissionLevel> higher = permissionSet.allowLevel(higherAction);
            Optional<PermissionLevel> lower = permissionSet.allowLevel(lowerAction);

            if (higher.isEmpty()) return;

            if (higher.get().isAll() && (lower.isEmpty() || lower.get().isNone())) {
                errors.add(String.format(
                        "Hierarchy violation: \"%s\" is granted at 'all' but \"%s\" is not granted. " +
                                "Granting edit access requires at least equivalent view access.",
                        higherAction, lowerAction));
            }
        }
    }

    public static final class Builder {
        private final List<HierarchyRule> hierarchyRules = new ArrayList<>();

        /**
         * Adds a hierarchy rule: granting the higher action requires the lower action
         * to also be granted.
         *
         * @param higherAction the action that implies the lower (e.g., "UpdateUser")
         * @param lowerAction the action that must be present (e.g., "ViewUser")
         */
        public Builder withHierarchy(String higherAction, String lowerAction) {
            hierarchyRules.add(new HierarchyRule(higherAction, lowerAction));
            return this;
        }

        public RoleConfigurationValidator build() {
            return new RoleConfigurationValidator(hierarchyRules);
        }
    }
}
