package com.ardley.acorn.permission;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * The access level granted or denied for a specific action within a role configuration.
 *
 * <p>Three variants exist:
 * <ul>
 *   <li>{@link None} — no access (explicit denial or absence)</li>
 *   <li>{@link All} — unrestricted access to all resources</li>
 *   <li>{@link Scoped} — access restricted by a {@link ScopeFilter}</li>
 * </ul>
 *
 * <p>JSON representation:
 * <ul>
 *   <li>{@code "none"} → None</li>
 *   <li>{@code "all"} → All</li>
 *   <li>{@code {"department": {"match": "principal"}}} → Scoped</li>
 * </ul>
 */
public sealed interface PermissionLevel {

    record None() implements PermissionLevel {}
    record All() implements PermissionLevel {}
    record Scoped(ScopeFilter filter) implements PermissionLevel {}

    static PermissionLevel none() { return new None(); }
    static PermissionLevel all() { return new All(); }
    static PermissionLevel scoped(ScopeFilter filter) { return new Scoped(filter); }

    /**
     * Parses a permission level from its JSON representation.
     */
    static PermissionLevel fromJson(JsonNode node) {
        if (node.isTextual()) {
            return switch (node.asText()) {
                case "none" -> none();
                case "all" -> all();
                default -> throw new IllegalArgumentException(
                        "Invalid permission level: \"" + node.asText() + "\"");
            };
        }
        if (node.isObject()) {
            return scoped(ScopeFilter.fromJson(node));
        }
        throw new IllegalArgumentException(
                "Permission level must be a string or object, got: " + node.getNodeType());
    }

    default boolean isNone() { return this instanceof None; }
    default boolean isAll() { return this instanceof All; }
    default boolean isScoped() { return this instanceof Scoped; }

    default Optional<ScopeFilter> scopeFilter() {
        return this instanceof Scoped s ? Optional.of(s.filter()) : Optional.empty();
    }
}
