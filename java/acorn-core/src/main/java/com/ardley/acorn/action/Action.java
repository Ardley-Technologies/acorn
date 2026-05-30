package com.ardley.acorn.action;

/**
 * Represents a discrete permission action in the authorization system.
 *
 * <p>Actions are the unit of permission — each entry in a role's allow/deny map
 * corresponds to an action name. Implementations are typically defined as enum
 * constants for type safety and discoverability:
 *
 * <pre>{@code
 * public enum UserActions implements Action {
 *     ListUsers("List all users in the workspace"),
 *     ViewUser("View a single user's details"),
 *     UpdateUser("Modify a user's attributes"),
 *     DeleteUser("Permanently remove a user");
 *
 *     private final String description;
 *
 *     UserActions(String description) { this.description = description; }
 *
 *     // Enum.name() satisfies Action.name() automatically
 *     public String description() { return description; }
 * }
 * }</pre>
 */
public interface Action {

    /**
     * The unique machine-readable identifier for this action.
     * Used as the key in permission set allow/deny maps.
     */
    String name();

    /**
     * A human-readable description of what this action permits.
     *
     * <p>This value is exposed via the {@link ActionRegistry} and intended to be
     * served to API callers so administrators can see the complete list of
     * available actions when constructing or editing role permission documents.
     */
    String description();
}
