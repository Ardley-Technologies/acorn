# @ardley/acorn-roles

Role management for multi-tenant applications. Provides a repository interface you implement for your database, a seeding service for default roles, and a bridge to Acorn's permission loading.

The pattern: you define your default roles in a JSON manifest, implement a repository for your persistence layer (Postgres, DynamoDB, Mongo, whatever), and this package handles initialization logic and connects it to the authorization engine.

## Install

```bash
bun add @ardley/acorn-core @ardley/acorn-roles
```

## Define your default roles

Create a manifest — a versioned list of roles to seed for new tenants:

```json
{
  "version": 1,
  "roles": [
    {
      "roleId": "admin",
      "roleName": "Admin",
      "description": "Full access to all resources",
      "systemRole": true,
      "assignableRoles": ["editor", "viewer"],
      "configuration": { "allow": "all" }
    },
    {
      "roleId": "editor",
      "roleName": "Editor",
      "description": "Create and modify, but can't delete or manage users",
      "systemRole": true,
      "assignableRoles": ["viewer"],
      "configuration": {
        "allow": { "ListUsers": "all", "UpdateUser": "all", "CreateUser": "all" },
        "deny": { "DeleteUser": "all", "ManageRoles": "all" }
      }
    },
    {
      "roleId": "viewer",
      "roleName": "Viewer",
      "description": "Read-only access",
      "systemRole": true,
      "assignableRoles": [],
      "configuration": {
        "allow": { "ListUsers": "all", "ViewUser": "all" }
      }
    }
  ]
}
```

The `configuration` field is a standard Acorn permission set. Same format everywhere.

## Implement the repository

This is the only thing you write. Five methods, all straightforward:

```typescript
import type { RoleConfigurationRepository, RoleRecord } from '@ardley/acorn-roles';

class MyRoleRepo implements RoleConfigurationRepository {
  async save(record: RoleRecord): Promise<void> { /* INSERT or UPSERT */ }
  async findById(tenantId: string, roleId: string): Promise<RoleRecord | undefined> { /* SELECT */ }
  async listByTenant(tenantId: string): Promise<RoleRecord[]> { /* SELECT WHERE tenant_id = ? */ }
  async delete(tenantId: string, roleId: string): Promise<void> { /* DELETE */ }
  async exists(tenantId: string, roleId: string): Promise<boolean> { /* SELECT 1 */ }
}
```

## Seed roles for new tenants

```typescript
import { RoleInitializationService } from '@ardley/acorn-roles';
import manifest from './default-roles.json';

const seeder = new RoleInitializationService(manifest);

// Call when creating a tenant, or on first access
const result = await seeder.initializeIfNeeded('tenant-abc', 0, myRepo);
// result.initialized = true, result.rolesCreated = 3
```

The service is safe to call repeatedly:
- If the tenant's version is already at or above the manifest version, it does nothing
- It never overwrites existing roles — if `admin` already exists for this tenant, it's left alone
- Only missing roles are created

This lets you bump the manifest version, add new roles, and re-run — existing customized roles stay intact, new system roles get added.

## Connect to authorization

The `RepositoryPermissionLoader` bridges your repo to Acorn's permission store interface:

```typescript
import { RepositoryPermissionLoader } from '@ardley/acorn-roles';
import { CachingPermissionStore } from '@ardley/acorn-core';

const loader = new RepositoryPermissionLoader(myRepo);
const store = new CachingPermissionStore(loader, { ttlMs: 300_000, maxSize: 10_000 });
```

Now pass `store` to your framework adapter. When a request comes in, Acorn calls `store.getPermissionSet(['tenant-abc', 'editor'])` — the loader fetches the role config from your DB, parses the permission JSON, and the cache keeps it hot for 5 minutes.

Your `Principal` implementation just needs to return the right key:

```typescript
const principal = {
  attribute: (name) => claims[name],
  permissionKey: () => [claims.tenant_id, claims.role],
};
```

## The `permissionKey()` contract

`RepositoryPermissionLoader` reads the key as **`[tenantId, roleId]`, in that order**. This is a load-bearing convention — role configurations are per-tenant (`RoleRecord.tenantId`), so returning just `[role]` would let tenant A's customized `admin` config leak into tenant B.

Rules for `Principal.permissionKey()` when using this loader:

- **Must return exactly two elements.** A single-element key causes the loader to return `undefined`, which the framework adapter converts to `AuthorizationDeniedError.noPermissions` (HTTP 403). Fail-closed, not a leak — but a 403 for every request is a bad way to discover the mistake.
- **Order matters.** `[tenantId, roleId]`. Never `[roleId, tenantId]`.
- **Both values must be non-empty strings.** The loader treats empty strings as missing.
- **The cache key is `key.join('::')`.** If you use a custom loader with a different key shape, keep the join stable so `CachingPermissionStore` doesn't collide.

If you implement a custom `PermissionLoader`, you may pick any key shape you like — but the shape must be consistent with what `Principal.permissionKey()` returns. The `[tenantId, roleId]` contract only applies when using `RepositoryPermissionLoader`.

## License

MIT
