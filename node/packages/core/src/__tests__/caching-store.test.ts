import { describe, test, expect } from 'bun:test';
import { CachingPermissionStore } from '../caching-store.js';
import { PermissionSet } from '../permission-set.js';
import type { PermissionLoader } from '../permission-store.js';

function mockLoader(data: Record<string, PermissionSet>): PermissionLoader & { calls: string[][] } {
  const calls: string[][] = [];
  return {
    calls,
    async load(key: string[]) {
      calls.push(key);
      return data[key.join('::')];
    },
  };
}

describe('CachingPermissionStore', () => {
  test('returns cached value on second call', async () => {
    const perms = PermissionSet.allowAll();
    const loader = mockLoader({ 'tenant::admin': perms });
    const store = new CachingPermissionStore(loader, { ttlMs: 60_000, maxSize: 100 });

    const first = await store.getPermissionSet(['tenant', 'admin']);
    const second = await store.getPermissionSet(['tenant', 'admin']);

    expect(first).toBe(perms);
    expect(second).toBe(perms);
    expect(loader.calls.length).toBe(1);
  });

  test('returns undefined for missing key', async () => {
    const loader = mockLoader({});
    const store = new CachingPermissionStore(loader, { ttlMs: 60_000, maxSize: 100 });

    const result = await store.getPermissionSet(['unknown']);
    expect(result).toBeUndefined();
  });

  test('invalidate forces reload', async () => {
    const perms = PermissionSet.allowAll();
    const loader = mockLoader({ 'tenant::admin': perms });
    const store = new CachingPermissionStore(loader, { ttlMs: 60_000, maxSize: 100 });

    await store.getPermissionSet(['tenant', 'admin']);
    store.invalidate(['tenant', 'admin']);
    await store.getPermissionSet(['tenant', 'admin']);

    expect(loader.calls.length).toBe(2);
  });

  test('evicts LRU when at capacity', async () => {
    const perms = PermissionSet.allowAll();
    const loader = mockLoader({
      'a': perms,
      'b': perms,
      'c': perms,
    });
    const store = new CachingPermissionStore(loader, { ttlMs: 60_000, maxSize: 2 });

    await store.getPermissionSet(['a']);
    await store.getPermissionSet(['b']);
    await store.getPermissionSet(['c']); // evicts 'a'
    await store.getPermissionSet(['a']); // must reload

    expect(loader.calls.length).toBe(4);
  });
});
