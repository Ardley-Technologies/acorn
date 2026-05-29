import { describe, test, expect } from 'bun:test';
import { ActionRegistry } from '../action-registry.js';
import { defineActions } from '../define-actions.js';

describe('ActionRegistry', () => {
  test('register and resolve', () => {
    const registry = new ActionRegistry();
    registry.register({ name: 'ListUsers', description: 'List users' });

    const action = registry.resolve('ListUsers');
    expect(action.name).toBe('ListUsers');
    expect(action.description).toBe('List users');
  });

  test('throws on duplicate', () => {
    const registry = new ActionRegistry();
    registry.register({ name: 'ListUsers', description: 'List users' });
    expect(() => registry.register({ name: 'ListUsers', description: 'dup' })).toThrow('Duplicate');
  });

  test('throws on unknown action', () => {
    const registry = new ActionRegistry();
    expect(() => registry.resolve('Unknown')).toThrow('Unknown action');
  });

  test('registerAll from defineActions', () => {
    const actions = defineActions({
      ListUsers: 'List users',
      UpdateUser: 'Update user',
      DeleteUser: 'Delete user',
    });

    const registry = new ActionRegistry();
    registry.registerAll(actions);

    expect(registry.size()).toBe(3);
    expect(registry.resolve('UpdateUser').description).toBe('Update user');
  });

  test('all() returns registered actions', () => {
    const registry = new ActionRegistry();
    registry.register({ name: 'A', description: 'a' });
    registry.register({ name: 'B', description: 'b' });

    expect(registry.all().length).toBe(2);
  });
});

describe('defineActions', () => {
  test('derives name from key', () => {
    const actions = defineActions({
      ListUsers: 'List all users',
      UpdateUser: 'Modify a user',
    });

    expect(actions.ListUsers.name).toBe('ListUsers');
    expect(actions.ListUsers.description).toBe('List all users');
    expect(actions.UpdateUser.name).toBe('UpdateUser');
  });

  test('result is frozen', () => {
    const actions = defineActions({ Foo: 'bar' });
    expect(Object.isFrozen(actions)).toBe(true);
    expect(Object.isFrozen(actions.Foo)).toBe(true);
  });
});
