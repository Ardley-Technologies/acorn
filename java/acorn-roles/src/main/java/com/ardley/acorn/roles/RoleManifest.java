package com.ardley.acorn.roles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A versioned manifest of default roles to seed for new tenants.
 *
 * <p>JSON format:
 * <pre>{@code
 * {
 *   "version": 1,
 *   "roles": [
 *     {
 *       "roleId": "admin",
 *       "roleName": "Admin",
 *       "description": "Full access",
 *       "systemRole": true,
 *       "assignableRoles": ["editor", "viewer"],
 *       "configuration": {"allow": "all"}
 *     }
 *   ]
 * }
 * }</pre>
 */
public record RoleManifest(int version, List<RoleDefinition> roles) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonCreator
    public RoleManifest(
            @JsonProperty("version") int version,
            @JsonProperty("roles") List<RoleDefinition> roles) {
        this.version = version;
        this.roles = List.copyOf(roles);
    }

    public static RoleManifest fromJson(String json) {
        try {
            return MAPPER.readValue(json, RoleManifest.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid role manifest JSON: " + e.getMessage(), e);
        }
    }

    public static RoleManifest fromFile(Path path) {
        try {
            String json = Files.readString(path);
            return fromJson(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read role manifest: " + e.getMessage(), e);
        }
    }

    public static RoleManifest fromResource(String resourcePath) {
        try (InputStream is = RoleManifest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return MAPPER.readValue(is, RoleManifest.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read role manifest resource: " + e.getMessage(), e);
        }
    }

    public record RoleDefinition(
            @JsonProperty("roleId") String roleId,
            @JsonProperty("roleName") String roleName,
            @JsonProperty("description") String description,
            @JsonProperty("systemRole") boolean systemRole,
            @JsonProperty("assignableRoles") List<String> assignableRoles,
            @JsonProperty("configuration") JsonNode configuration) {

        public RoleDefinition {
            assignableRoles = assignableRoles != null ? List.copyOf(assignableRoles) : List.of();
        }

        public String configurationJson() {
            return configuration.toString();
        }
    }
}
