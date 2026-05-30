package com.ardley.acorn.roles;

import com.ardley.acorn.permission.PermissionSet;
import com.ardley.acorn.store.PermissionLoader;

import java.util.List;
import java.util.Optional;

/**
 * A {@link PermissionLoader} backed by a {@link RoleConfigurationRepository}.
 *
 * <p>Expects permission keys with two segments: {@code [tenantId, roleId]}.
 * Fetches the role configuration and parses its permission JSON.
 *
 * <p>Wrap with {@link com.ardley.acorn.store.CachingPermissionStore} for production use.
 *
 * <pre>{@code
 * PermissionLoader loader = new RepositoryPermissionLoader(myRepo);
 * PermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
 * }</pre>
 */
public final class RepositoryPermissionLoader implements PermissionLoader {

    private final RoleConfigurationRepository repository;

    public RepositoryPermissionLoader(RoleConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PermissionSet> load(List<String> key) {
        if (key.size() < 2) {
            return Optional.empty();
        }
        String tenantId = key.get(0);
        String roleId = key.get(1);

        return repository.findById(tenantId, roleId)
                .map(record -> PermissionSet.fromJson(record.configuration()));
    }
}
