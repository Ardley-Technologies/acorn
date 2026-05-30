import type { RoleRecord } from './types.js';

export interface RoleConfigurationRepository {
  save(record: RoleRecord): Promise<void>;
  findById(tenantId: string, roleId: string): Promise<RoleRecord | undefined>;
  listByTenant(tenantId: string): Promise<RoleRecord[]>;
  delete(tenantId: string, roleId: string): Promise<void>;
  exists(tenantId: string, roleId: string): Promise<boolean>;
}
