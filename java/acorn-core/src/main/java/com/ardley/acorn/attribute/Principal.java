package com.ardley.acorn.attribute;

import java.util.List;

/**
 * Represents an authenticated principal in the authorization system.
 *
 * <p>Extends {@link AttributeSource} to provide named attributes for scope filter
 * evaluation, and adds a {@link #permissionKey()} for looking up the principal's
 * permission set from the store.
 *
 * <p>Implementations wrap your application's authentication types — JWT claims,
 * session objects, API key records, or any other identity representation.
 *
 * <p>Example implementation:
 * <pre>{@code
 * public class JwtPrincipal implements Principal {
 *     private final Map<String, String> claims;
 *     private final String tenantId;
 *     private final String role;
 *
 *     public Optional<String> attribute(String name) {
 *         return Optional.ofNullable(claims.get(name));
 *     }
 *
 *     public List<String> permissionKey() {
 *         return List.of(tenantId, role);
 *     }
 * }
 * }</pre>
 */
public interface Principal extends AttributeSource {

    /**
     * Key segments used to look up this principal's permission set from the
     * {@link com.ardley.acorn.store.PermissionStore}.
     *
     * <p>The interpretation of segments is entirely up to the store implementation.
     * Common patterns:
     * <ul>
     *   <li>Multi-tenant SaaS: {@code List.of("tenant-abc", "manager")}</li>
     *   <li>Single-tenant: {@code List.of("editor")}</li>
     *   <li>Hierarchical: {@code List.of("org-1", "team-frontend", "senior")}</li>
     * </ul>
     */
    List<String> permissionKey();
}
