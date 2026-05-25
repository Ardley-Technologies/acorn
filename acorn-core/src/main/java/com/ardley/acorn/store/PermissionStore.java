package com.ardley.acorn.store;

import com.ardley.acorn.permission.PermissionSet;

import java.util.List;
import java.util.Optional;

/**
 * Retrieves permission sets for principals, optionally with caching.
 *
 * <p>Implementations are responsible for loading permission configurations from
 * persistent storage and returning them for evaluation. Caching is recommended
 * on the hot path — see {@link CachingPermissionStore} for a ready-made solution.
 *
 * <p>The key is a list of string segments derived from the principal's
 * {@link com.ardley.acorn.attribute.Principal#permissionKey()} method.
 */
public interface PermissionStore {

    /**
     * Loads the permission set for the given key segments.
     *
     * @param key the permission key segments (e.g., ["tenant-abc", "manager"])
     * @return the permission set, or empty if no configuration exists
     */
    Optional<PermissionSet> getPermissionSet(List<String> key);

    /**
     * Invalidates any cached permission set for the given key.
     * Call after role configuration changes.
     *
     * @param key the permission key segments to invalidate
     */
    void invalidate(List<String> key);
}
