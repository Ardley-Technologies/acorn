import { describe, test, expect } from 'bun:test';
import { RepositoryPermissionLoader } from '../permission-loader.js';
import type { RoleConfigurationRepository } from '../repository.js';
import type { RoleRecord } from '../types.js';

function mockRepo(records: RoleRecord[]): RoleConfigurationRepository {
  return {
    async save() {},
    async findById(tenantId: string, roleId: string) {
      return records.find(r => r.tenantId === tenantId && r.roleId === roleId);
    },
    async listByTenant(tenantId: string) {
      return records.filter(r => r.tenantId === tenantId);
    },
    async delete() {},
    async exists(tenantId: string, roleId: string) {
      return records.some(r => r.tenantId === tenantId && r.roleId === roleId);
    },
  };
}

describe('RepositoryPermissionLoader', () => {
  test('loads and parses permission set from repository', async () => {
    const repo = mockRepo([{
      tenantId: 't-1',
      roleId: 'admin',
      roleName: 'Admin',
      description: '',
      systemRole: true,
      assignableRoles: [],
      configuration: '{"allow": "all"}',
      version: 1,
    }]);
    const loader = new RepositoryPermissionLoader(repo);

    const perms = await loader.load(['t-1', 'admin']);

    expect(perms).toBeDefined();
    expect(perms!.isAllowAll()).toBe(true);
  });

  test('returns undefined for missing role', async () => {
    const repo = mockRepo([]);
    const loader = new RepositoryPermissionLoader(repo);

    const perms = await loader.load(['t-1', 'nonexistent']);

    expect(perms).toBeUndefined();
  });

  test('returns undefined if key segments missing', async () => {
    const repo = mockRepo([]);
    const loader = new RepositoryPermissionLoader(repo);

    expect(await loader.load([])).toBeUndefined();
    expect(await loader.load(['t-1'])).toBeUndefined();
  });
});
