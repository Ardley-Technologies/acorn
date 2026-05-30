import { describe, test, expect } from 'bun:test';
import { PermissionSet } from '../permission-set.js';

describe('PermissionSet', () => {
  test('allowAll returns superadmin set', () => {
    const perms = PermissionSet.allowAll();
    expect(perms.isAllowAll()).toBe(true);
    expect(perms.hasAllowFor('anything')).toBe(true);
  });

  test('empty returns deny-all set', () => {
    const perms = PermissionSet.empty();
    expect(perms.isAllowAll()).toBe(false);
    expect(perms.hasAllowFor('anything')).toBe(false);
  });

  test('parses superadmin shorthand', () => {
    const perms = PermissionSet.fromJson('{"allow": "all"}');
    expect(perms.isAllowAll()).toBe(true);
  });

  test('parses allow map with "all" level', () => {
    const perms = PermissionSet.fromJson('{"allow": {"ListUsers": "all"}}');
    expect(perms.hasAllowFor('ListUsers')).toBe(true);
    expect(perms.hasAllowFor('Other')).toBe(false);
    const level = perms.allowLevel('ListUsers');
    expect(level?.type).toBe('all');
  });

  test('parses scoped allow', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { UpdateUser: { department: { match: 'principal' } } },
    }));
    const level = perms.allowLevel('UpdateUser');
    expect(level?.type).toBe('scoped');
  });

  test('parses deny map', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      allow: { ListUsers: 'all' },
      deny: { DeleteUser: 'all' },
    }));
    expect(perms.hasUnconditionalDeny('DeleteUser')).toBe(true);
    expect(perms.hasUnconditionalDeny('ListUsers')).toBe(false);
  });

  test('scoped deny is not unconditional', () => {
    const perms = PermissionSet.fromJson(JSON.stringify({
      deny: { UpdateUser: { department: { equals: 'Executive' } } },
    }));
    expect(perms.hasUnconditionalDeny('UpdateUser')).toBe(false);
    expect(perms.denyLevel('UpdateUser')?.type).toBe('scoped');
  });

  test('throws on invalid JSON', () => {
    expect(() => PermissionSet.fromJson('not json')).toThrow('Invalid permission JSON');
  });

  test('throws on invalid level string', () => {
    expect(() => PermissionSet.fromJson('{"allow": {"X": "bogus"}}')).toThrow('Invalid permission level');
  });
});
