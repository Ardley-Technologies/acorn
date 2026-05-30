import type { PermissionLoader } from '@ardley/acorn-core';
import { PermissionSet } from '@ardley/acorn-core';
import type { RoleConfigurationRepository } from './repository.js';

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
