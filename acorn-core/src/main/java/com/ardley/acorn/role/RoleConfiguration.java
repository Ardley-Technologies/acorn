package com.ardley.acorn.role;

import com.ardley.acorn.permission.PermissionSet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents a named role configuration within a tenant or organization.
 *
 * <p>A role configuration is the administrative unit that maps a human-readable role
 * (e.g., "Manager", "Loan Officer") to a {@link PermissionSet} defining what actions
 * that role can perform and under what scope constraints.
 *
 * <p>This is a data class — it carries no behavior beyond accessors. Persistence,
 * CRUD operations, and endpoint exposure are the responsibility of the consuming
 * application.
 *
 * <p>Key concepts:
 * <ul>
 *   <li><b>System roles</b> cannot be deleted by customers and are typically seeded
 *       at tenant creation (e.g., "admin", "read-only").</li>
 *   <li><b>Assignable roles</b> define which other roles a holder of this role may
 *       assign to users, preventing privilege escalation.</li>
 * </ul>
 */
public final class RoleConfiguration {

    private final String roleId;
    private final String roleName;
    private final String description;
    private final String permissionJson;
    private final boolean systemRole;
    private final List<String> assignableRoles;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RoleConfiguration(Builder builder) {
        this.roleId = Objects.requireNonNull(builder.roleId, "roleId is required");
        this.roleName = Objects.requireNonNull(builder.roleName, "roleName is required");
        this.description = builder.description;
        this.permissionJson = Objects.requireNonNull(builder.permissionJson, "permissionJson is required");
        this.systemRole = builder.systemRole;
        this.assignableRoles = builder.assignableRoles != null
                ? List.copyOf(builder.assignableRoles)
                : List.of();
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String roleId() { return roleId; }
    public String roleName() { return roleName; }
    public String description() { return description; }
    public String permissionJson() { return permissionJson; }
    public boolean isSystemRole() { return systemRole; }
    public List<String> assignableRoles() { return assignableRoles; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    /**
     * Parses the stored permission JSON into a {@link PermissionSet}.
     *
     * @return the parsed permission set
     * @throws IllegalArgumentException if the JSON is malformed
     */
    public PermissionSet toPermissionSet() {
        return PermissionSet.fromJson(permissionJson);
    }

    /**
     * Returns whether holders of this role are permitted to assign the specified target role.
     *
     * @param targetRoleId the role being assigned
     * @return true if assignment is permitted
     */
    public boolean canAssign(String targetRoleId) {
        return assignableRoles.contains(targetRoleId);
    }

    public static final class Builder {
        private String roleId;
        private String roleName;
        private String description;
        private String permissionJson;
        private boolean systemRole;
        private List<String> assignableRoles;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder roleId(String roleId) { this.roleId = roleId; return this; }
        public Builder roleName(String roleName) { this.roleName = roleName; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder permissionJson(String permissionJson) { this.permissionJson = permissionJson; return this; }
        public Builder systemRole(boolean systemRole) { this.systemRole = systemRole; return this; }
        public Builder assignableRoles(List<String> assignableRoles) { this.assignableRoles = assignableRoles; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public RoleConfiguration build() {
            return new RoleConfiguration(this);
        }
    }
}
