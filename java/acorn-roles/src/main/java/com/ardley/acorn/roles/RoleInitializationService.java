package com.ardley.acorn.roles;

/**
 * Seeds default roles for tenants that haven't been initialized or are behind
 * the current roles configuration version.
 *
 * <p>Safe to call repeatedly — will not overwrite existing roles. Only creates
 * roles that don't already exist for the tenant.
 *
 * <pre>{@code
 * RoleManifest manifest = RoleManifest.fromResource("default-roles.json");
 * RoleInitializationService service = new RoleInitializationService(manifest);
 *
 * InitializationResult result = service.initializeIfNeeded("tenant-abc", 0, repository);
 * if (result.initialized()) {
 *     log.info("Seeded {} roles", result.rolesCreated());
 * }
 * }</pre>
 */
public final class RoleInitializationService {

    private final RoleManifest manifest;

    public RoleInitializationService(RoleManifest manifest) {
        this.manifest = manifest;
    }

    public int currentVersion() {
        return manifest.version();
    }

    /**
     * Initializes default roles for a tenant if their version is behind the manifest.
     *
     * @param tenantId the tenant to initialize
     * @param tenantRolesVersion the tenant's current roles version (0 if never initialized)
     * @param repository the repository to persist roles into
     * @return the result indicating what was done
     */
    public InitializationResult initializeIfNeeded(
            String tenantId,
            int tenantRolesVersion,
            RoleConfigurationRepository repository) {

        if (tenantRolesVersion >= manifest.version()) {
            return new InitializationResult(false, 0);
        }

        int created = 0;
        for (RoleManifest.RoleDefinition role : manifest.roles()) {
            if (!repository.exists(tenantId, role.roleId())) {
                RoleRecord record = RoleRecord.builder()
                        .tenantId(tenantId)
                        .roleId(role.roleId())
                        .roleName(role.roleName())
                        .description(role.description())
                        .systemRole(role.systemRole())
                        .assignableRoles(role.assignableRoles())
                        .configuration(role.configurationJson())
                        .version(manifest.version())
                        .build();
                repository.save(record);
                created++;
            }
        }

        return new InitializationResult(true, created);
    }

    public record InitializationResult(boolean initialized, int rolesCreated) {}
}
