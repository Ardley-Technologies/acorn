import type { PermissionLoader } from '@ardley-technologies/acorn-core';
import { PermissionSet } from '@ardley-technologies/acorn-core';
import type { RoleConfigurationRepository } from './repository.js';

/**
 * Loads permission sets from a `RoleConfigurationRepository`.
 *
 * The key contract is `[tenantId, roleId]`, in that order. This is
 * load-bearing: role configurations are per-tenant, so a single-element or
 * reordered key would let one tenant's customized role config be served to
 * another. Malformed keys return `undefined`, which framework adapters
 * convert to `AuthorizationDeniedError.noPermissions` (HTTP 403) — a
 * fail-closed default rather than a cross-tenant leak.
 *
 * `Principal.permissionKey()` implementations MUST return
 * `[tenantId, roleId]` when paired with this loader.
 */
export class RepositoryPermissionLoader implements PermissionLoader {
  constructor(private readonly repository: RoleConfigurationRepository) {}

  async load(key: string[]): Promise<PermissionSet | undefined> {
    const tenantId = key[0];
    const roleId = key[1];
    if (!tenantId || !roleId) return undefined;

    const record = await this.repository.findById(tenantId, roleId);
    if (!record) return undefined;

    return PermissionSet.fromJson(record.configuration);
  }
}
