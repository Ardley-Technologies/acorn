package com.ardley.acorn.roles;

import java.util.List;
import java.util.Objects;

/**
 * A stored role configuration record.
 * Represents a single role within a tenant, persisted in the user's database.
 */
public final class RoleRecord {

    private final String tenantId;
    private final String roleId;
    private final String roleName;
    private final String description;
    private final boolean systemRole;
    private final List<String> assignableRoles;
    private final String configuration;
    private final int version;

    private RoleRecord(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
        this.roleId = Objects.requireNonNull(builder.roleId, "roleId is required");
        this.roleName = Objects.requireNonNull(builder.roleName, "roleName is required");
        this.description = builder.description != null ? builder.description : "";
        this.systemRole = builder.systemRole;
        this.assignableRoles = builder.assignableRoles != null
                ? List.copyOf(builder.assignableRoles) : List.of();
        this.configuration = Objects.requireNonNull(builder.configuration, "configuration is required");
        this.version = builder.version;
    }

    public static Builder builder() { return new Builder(); }

    public String tenantId() { return tenantId; }
    public String roleId() { return roleId; }
    public String roleName() { return roleName; }
    public String description() { return description; }
    public boolean isSystemRole() { return systemRole; }
    public List<String> assignableRoles() { return assignableRoles; }
    public String configuration() { return configuration; }
    public int version() { return version; }

    public static final class Builder {
        private String tenantId;
        private String roleId;
        private String roleName;
        private String description;
        private boolean systemRole;
        private List<String> assignableRoles;
        private String configuration;
        private int version;

        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder roleId(String roleId) { this.roleId = roleId; return this; }
        public Builder roleName(String roleName) { this.roleName = roleName; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder systemRole(boolean systemRole) { this.systemRole = systemRole; return this; }
        public Builder assignableRoles(List<String> assignableRoles) { this.assignableRoles = assignableRoles; return this; }
        public Builder configuration(String configuration) { this.configuration = configuration; return this; }
        public Builder version(int version) { this.version = version; return this; }

        public RoleRecord build() { return new RoleRecord(this); }
    }
}
