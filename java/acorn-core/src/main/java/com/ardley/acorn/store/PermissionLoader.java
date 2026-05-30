package com.ardley.acorn.store;

import com.ardley.acorn.permission.PermissionSet;

import java.util.List;
import java.util.Optional;

/**
 * Low-level interface for fetching raw permission configurations from storage.
 *
 * <p>Wrap with {@link CachingPermissionStore} for production use. Implementations
 * should not cache — that concern is handled at the store layer.
 */
public interface PermissionLoader {

    /**
     * Loads a permission set from storage.
     *
     * @param key the permission key segments
     * @return the parsed permission set, or empty if not found
     */
    Optional<PermissionSet> load(List<String> key);
}
