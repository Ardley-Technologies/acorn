import type { RoleManifest, RoleRecord } from './types.js';
import type { RoleConfigurationRepository } from './repository.js';

export interface InitializationResult {
  readonly initialized: boolean;
  readonly rolesCreated: number;
}

export class RoleInitializationService {
  private readonly manifest: RoleManifest;

  constructor(manifest: RoleManifest) {
    this.manifest = manifest;
  }

  static fromJson(json: string): RoleInitializationService {
    const manifest = JSON.parse(json) as RoleManifest;
    return new RoleInitializationService(manifest);
  }

  static fromFile(path: string): RoleInitializationService {
    const fs = require('fs');
    const json = fs.readFileSync(path, 'utf-8');
    return RoleInitializationService.fromJson(json);
  }

  currentVersion(): number {
    return this.manifest.version;
  }

  roles(): readonly RoleManifest['roles'][number][] {
    return this.manifest.roles;
  }

  async initializeIfNeeded(
    tenantId: string,
    tenantRolesVersion: number,
    repository: RoleConfigurationRepository,
  ): Promise<InitializationResult> {
    if (tenantRolesVersion >= this.manifest.version) {
      return { initialized: false, rolesCreated: 0 };
    }

    let rolesCreated = 0;

    for (const role of this.manifest.roles) {
      const exists = await repository.exists(tenantId, role.roleId);
      if (!exists) {
        const record: RoleRecord = {
          tenantId,
          roleId: role.roleId,
          roleName: role.roleName,
          description: role.description,
          systemRole: role.systemRole,
          assignableRoles: role.assignableRoles,
          configuration: JSON.stringify(role.configuration),
          version: this.manifest.version,
        };
        await repository.save(record);
        rolesCreated++;
      }
    }

    return { initialized: true, rolesCreated };
  }
}
