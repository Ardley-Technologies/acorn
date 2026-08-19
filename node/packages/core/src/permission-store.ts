import type { PermissionSet } from './permission-set.js';

/**
 * Loads a permission set for a given key. Implementations decide what the
 * key means — a common shape is `[tenantId, roleId]`, which is what
 * `@ardley-technologies/acorn-roles`' `RepositoryPermissionLoader` uses.
 *
 * The key shape is a contract between the loader and `Principal.permissionKey()`.
 * Return `undefined` when the key is malformed or no permission set exists;
 * the framework adapter converts that into an authorization denial.
 */
export interface PermissionLoader {
  load(key: string[]): Promise<PermissionSet | undefined>;
}

/**
 * Fronts a `PermissionLoader` with a lookup interface used by the framework
 * adapters. `CachingPermissionStore` is the standard implementation — it
 * caches loader responses keyed by `key.join('::')`.
 */
export interface PermissionStore {
  getPermissionSet(key: string[]): Promise<PermissionSet | undefined>;
  invalidate(key: string[]): void;
}
