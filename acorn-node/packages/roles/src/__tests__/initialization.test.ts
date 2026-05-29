import { describe, test, expect } from 'bun:test';
import { RoleInitializationService } from '../initialization.js';
import type { RoleConfigurationRepository } from '../repository.js';
import type { RoleRecord } from '../types.js';

const manifest = {
  version: 1,
  roles: [
    {
      roleId: 'admin',
      roleName: 'Admin',
      description: 'Full access',
      systemRole: true,
      assignableRoles: ['editor', 'viewer'],
      configuration: { allow: 'all' },
    },
    {
      roleId: 'editor',
      roleName: 'Editor',
      description: 'Edit access',
      systemRole: true,
      assignableRoles: ['viewer'],
      configuration: {
        allow: { ListUsers: 'all', UpdateUser: 'all' },
        deny: { DeleteUser: 'all' },
      },
    },
    {
      roleId: 'viewer',
      roleName: 'Viewer',
      description: 'Read-only access',
      systemRole: true,
      assignableRoles: [],
      configuration: { allow: { ListUsers: 'all' } },
    },
  ],
};

function mockRepo(existing: RoleRecord[] = []): RoleConfigurationRepository & { saved: RoleRecord[] } {
  const saved = [...existing];
  return {
    saved,
    async save(record: RoleRecord) { saved.push(record); },
    async findById(tenantId: string, roleId: string) {
      return saved.find(r => r.tenantId === tenantId && r.roleId === roleId);
    },
    async listByTenant(tenantId: string) {
      return saved.filter(r => r.tenantId === tenantId);
    },
    async delete(tenantId: string, roleId: string) {
      const idx = saved.findIndex(r => r.tenantId === tenantId && r.roleId === roleId);
      if (idx >= 0) saved.splice(idx, 1);
    },
    async exists(tenantId: string, roleId: string) {
      return saved.some(r => r.tenantId === tenantId && r.roleId === roleId);
    },
  };
}

describe('RoleInitializationService', () => {
  test('seeds all roles for new tenant', async () => {
    const svc = new RoleInitializationService(manifest);
    const repo = mockRepo();

    const result = await svc.initializeIfNeeded('t-1', 0, repo);

    expect(result.initialized).toBe(true);
    expect(result.rolesCreated).toBe(3);
    expect(repo.saved.length).toBe(3);
    expect(repo.saved.map(r => r.roleId).sort()).toEqual(['admin', 'editor', 'viewer']);
  });

  test('skips if already at current version', async () => {
    const svc = new RoleInitializationService(manifest);
    const repo = mockRepo();

    const result = await svc.initializeIfNeeded('t-1', 1, repo);

    expect(result.initialized).toBe(false);
    expect(result.rolesCreated).toBe(0);
    expect(repo.saved.length).toBe(0);
  });

  test('does not overwrite existing roles', async () => {
    const svc = new RoleInitializationService(manifest);
    const existing: RoleRecord = {
      tenantId: 't-1',
      roleId: 'admin',
      roleName: 'Admin',
      description: 'Custom admin',
      systemRole: true,
      assignableRoles: [],
      configuration: '{"allow":"all"}',
      version: 0,
    };
    const repo = mockRepo([existing]);

    const result = await svc.initializeIfNeeded('t-1', 0, repo);

    expect(result.initialized).toBe(true);
    expect(result.rolesCreated).toBe(2);
    // admin preserved, editor + viewer added
    expect(repo.saved.length).toBe(3);
  });

  test('stores configuration as JSON string', async () => {
    const svc = new RoleInitializationService(manifest);
    const repo = mockRepo();

    await svc.initializeIfNeeded('t-1', 0, repo);

    const admin = repo.saved.find(r => r.roleId === 'admin')!;
    expect(JSON.parse(admin.configuration)).toEqual({ allow: 'all' });
  });

  test('fromJson parses manifest', () => {
    const svc = RoleInitializationService.fromJson(JSON.stringify(manifest));
    expect(svc.currentVersion()).toBe(1);
    expect(svc.roles().length).toBe(3);
  });

  test('currentVersion returns manifest version', () => {
    const svc = new RoleInitializationService(manifest);
    expect(svc.currentVersion()).toBe(1);
  });
});
