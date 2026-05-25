package com.ardley.acorn.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A role's complete permission configuration defining which actions are allowed or denied
 * and under what scope constraints.
 *
 * <p>The permission set is the central data structure of Acorn's authorization model.
 * It is loaded from persistent storage via the
 * {@link com.ardley.acorn.store.PermissionStore} and evaluated by the
 * {@link com.ardley.acorn.evaluator.Evaluator}.
 *
 * <p>JSON format:
 * <pre>{@code
 * {
 *   "allow": {
 *     "ListUsers": "all",
 *     "UpdateUser": {"department": {"match": "principal"}}
 *   },
 *   "deny": {
 *     "DeleteUser": "all"
 *   }
 * }
 * }</pre>
 *
 * <p>Superadmin shorthand: {@code {"allow": "all"}}
 */
public final class PermissionSet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean allowAll;
    private final Map<String, PermissionLevel> allow;
    private final Map<String, PermissionLevel> deny;

    private PermissionSet(boolean allowAll, Map<String, PermissionLevel> allow, Map<String, PermissionLevel> deny) {
        this.allowAll = allowAll;
        this.allow = Map.copyOf(allow);
        this.deny = Map.copyOf(deny);
    }

    public static PermissionSet allowAll() {
        return new PermissionSet(true, Map.of(), Map.of());
    }

    public static PermissionSet empty() {
        return new PermissionSet(false, Map.of(), Map.of());
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public boolean hasAllowFor(String actionName) {
        if (allowAll) return true;
        PermissionLevel level = allow.get(actionName);
        return level != null && !level.isNone();
    }

    public boolean hasUnconditionalDeny(String actionName) {
        PermissionLevel level = deny.get(actionName);
        return level != null && level.isAll();
    }

    public Optional<PermissionLevel> allowLevel(String actionName) {
        return Optional.ofNullable(allow.get(actionName));
    }

    public Optional<PermissionLevel> denyLevel(String actionName) {
        return Optional.ofNullable(deny.get(actionName));
    }

    /**
     * Parses a permission set from a JSON string.
     * Supports the {@code {"allow": "all"}} shorthand for superadmin roles.
     *
     * @param json the JSON string to parse
     * @return the parsed permission set
     * @throws IllegalArgumentException if the JSON is malformed
     */
    public static PermissionSet fromJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            return fromJsonNode(root);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid permission JSON: " + e.getMessage(), e);
        }
    }

    public static PermissionSet fromJsonNode(JsonNode root) {
        JsonNode allowNode = root.get("allow");
        JsonNode denyNode = root.get("deny");

        if (allowNode != null && allowNode.isTextual() && "all".equals(allowNode.asText())) {
            return new PermissionSet(true, Map.of(), parseLevelMap(denyNode));
        }

        return new PermissionSet(false, parseLevelMap(allowNode), parseLevelMap(denyNode));
    }

    private static Map<String, PermissionLevel> parseLevelMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        Map<String, PermissionLevel> map = new HashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            map.put(entry.getKey(), PermissionLevel.fromJson(entry.getValue()));
        }
        return map;
    }
}
