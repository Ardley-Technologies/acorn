import { describe, test, expect } from 'bun:test';
import { canPerformAction, evaluate } from '../evaluator.js';
import { PermissionSet } from '../permission-set.js';
import { EvaluationPolicy } from '../evaluation-policy.js';
import { Attributes } from '../attributes.js';
import type { Action } from '../types.js';

const UPDATE_USER: Action = { name: 'UpdateUser', description: 'Update a user' };
const DELETE_USER: Action = { name: 'DeleteUser', description: 'Delete a user' };
const LIST_USERS: Action = { name: 'ListUsers', description: 'List users' };

const TENANT_ISOLATION = EvaluationPolicy.withIsolation('tenant_id');
const NO_ISOLATION = EvaluationPolicy.none();

describe('Gate checks (canPerformAction)', () => {
  test('allowAll permission set permits any action', () => {
    const perms = PermissionSet.allowAll();
    const result = canPerformAction(perms, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('empty permission set denies all actions', () => {
    const perms = PermissionSet.empty();
    const result = canPerformAction(perms, UPDATE_USER);
    expect(result.permitted).toBe(false);
    expect(result.reason).toContain('UpdateUser');
  });

  test('unconditional deny overrides allowAll', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: 'all',
      deny: { DeleteUser: 'all' },
    }));
    expect(canPerformAction(perms, DELETE_USER).permitted).toBe(false);
    expect(canPerformAction(perms, UPDATE_USER).permitted).toBe(true);
  });

  test('scoped allow still passes gate check', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: { department: { match: 'principal' } } },
    }));
    const result = canPerformAction(perms, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('action not in allow map is implicitly denied', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { ListUsers: 'all' },
    }));
    expect(canPerformAction(perms, UPDATE_USER).permitted).toBe(false);
    expect(canPerformAction(perms, LIST_USERS).permitted).toBe(true);
  });
});

describe('Full evaluation (evaluate)', () => {
  test('isolation violation: different tenants', () => {
    const perms = PermissionSet.allowAll();
    const principal = Attributes.from({ tenant_id: 'tenant-A', department: 'Eng' });
    const resource = Attributes.from({ tenant_id: 'tenant-B' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(false);
    expect(result.reason).toContain('Isolation violation');
  });

  test('isolation passes when resource lacks isolation attribute', () => {
    const perms = PermissionSet.allowAll();
    const principal = Attributes.from({ tenant_id: 'tenant-A' });
    const resource = Attributes.from({ status: 'active' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('no isolation policy allows cross-tenant access', () => {
    const perms = PermissionSet.allowAll();
    const principal = Attributes.from({ tenant_id: 'tenant-A' });
    const resource = Attributes.from({ tenant_id: 'tenant-B' });

    const result = evaluate(perms, principal, resource, NO_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('scoped allow matches when principal and resource share department', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: { department: { match: 'principal' } } },
    }));
    const principal = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });
    const resource = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('scoped allow denies when departments differ', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: { department: { match: 'principal' } } },
    }));
    const principal = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });
    const resource = Attributes.from({ tenant_id: 't-1', department: 'Sales' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(false);
    expect(result.reason).toContain('scope filter did not match');
  });

  test('scoped deny blocks access even when allow is unconditional', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: 'all' },
      deny: { UpdateUser: { department: { equals: 'Executive' } } },
    }));
    const principal = Attributes.from({ tenant_id: 't-1' });
    const resource = Attributes.from({ tenant_id: 't-1', department: 'Executive' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(false);
    expect(result.reason).toContain('scope matched');
  });

  test('scoped deny does not block when scope filter does not match', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: 'all' },
      deny: { UpdateUser: { department: { equals: 'Executive' } } },
    }));
    const principal = Attributes.from({ tenant_id: 't-1' });
    const resource = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, UPDATE_USER);
    expect(result.permitted).toBe(true);
  });

  test('unconditional deny takes precedence over unconditional allow', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { DeleteUser: 'all' },
      deny: { DeleteUser: 'all' },
    }));
    const principal = Attributes.from({ tenant_id: 't-1' });
    const resource = Attributes.from({ tenant_id: 't-1' });

    const result = evaluate(perms, principal, resource, TENANT_ISOLATION, DELETE_USER);
    expect(result.permitted).toBe(false);
  });

  test('multi-dimensional scope filter requires ALL dimensions to match', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: { department: { match: 'principal' }, status: { equals: 'active' } } },
    }));
    const principal = Attributes.from({ tenant_id: 't-1', department: 'Eng' });

    // Both match
    const activeEng = Attributes.from({ tenant_id: 't-1', department: 'Eng', status: 'active' });
    expect(evaluate(perms, principal, activeEng, TENANT_ISOLATION, UPDATE_USER).permitted).toBe(true);

    // Department matches but status doesn't
    const inactiveEng = Attributes.from({ tenant_id: 't-1', department: 'Eng', status: 'suspended' });
    expect(evaluate(perms, principal, inactiveEng, TENANT_ISOLATION, UPDATE_USER).permitted).toBe(false);

    // Status matches but department doesn't
    const activeSales = Attributes.from({ tenant_id: 't-1', department: 'Sales', status: 'active' });
    expect(evaluate(perms, principal, activeSales, TENANT_ISOLATION, UPDATE_USER).permitted).toBe(false);
  });
});
