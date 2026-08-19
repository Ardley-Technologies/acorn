# @ardley-technologies/acorn-core

The authorization engine. Zero runtime dependencies. Framework-agnostic. This is where all the actual decision-making lives.

You probably don't use this package alone — you pair it with `@ardley-technologies/acorn-express`, `@ardley-technologies/acorn-fastify`, or `@ardley-technologies/acorn-koa`. But if you're building your own integration or just want to evaluate permissions in a script, this is all you need.

## Install

```bash
bun add @ardley-technologies/acorn-core
```

## Actions

Every permission references an action by name. You define actions once, and the names are derived from the keys — no redundant `name: "UpdateUser"` boilerplate:

```typescript
import { defineActions, ActionRegistry } from '@ardley-technologies/acorn-core';

const UserActions = defineActions({
  ListUsers: 'List all users in the workspace',
  UpdateUser: 'Modify user attributes',
  DeleteUser: 'Permanently remove a user',
});

// UserActions.UpdateUser → { name: 'UpdateUser', description: 'Modify user attributes' }

const registry = new ActionRegistry();
registry.registerAll(UserActions);

// For admin UIs that need to list available actions:
registry.all(); // → [{ name: 'ListUsers', description: '...' }, ...]
```

## Evaluating permissions

The evaluator is stateless. It takes inputs and returns a decision — no side effects, no I/O.

```typescript
import { canPerformAction, evaluate, PermissionSet, EvaluationPolicy, Attributes } from '@ardley-technologies/acorn-core';

const perms = PermissionSet.fromJson(`{
  "allow": { "UpdateUser": {"department": {"match": "principal"}} },
  "deny": { "DeleteUser": "all" }
}`);

// Gate check: can this role do this action at all?
canPerformAction(perms, UserActions.UpdateUser);
// → { permitted: true }

// Full check: can this principal do this action on THIS resource?
const principal = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });
const resource = Attributes.from({ tenant_id: 't-1', department: 'Engineering' });
const policy = EvaluationPolicy.withIsolation('tenant_id');

evaluate(perms, principal, resource, policy, UserActions.UpdateUser);
// → { permitted: true }
```

Change the resource's department to "Sales" and the same call returns `{ permitted: false, reason: 'Action "UpdateUser" scope filter did not match' }`.

## Evaluation order

This is the core contract. It never changes:

1. **Isolation check** — if the resource belongs to a different tenant, deny immediately
2. **Unconditional deny** — if the action is denied at "all" scope, done
3. **Scoped deny** — if a deny filter matches the resource, done
4. **Allow all** — if this is a superadmin set, allow
5. **Unconditional allow** — if the action is allowed at "all" scope, allow
6. **Scoped allow** — if an allow filter matches the resource, allow
7. **Implicit deny** — nothing matched, deny

Deny wins. Always.

`canPerformAction()` is a **gate check** — it runs steps 2 and 5 only (unconditional deny → allow). No resource is involved, so the isolation check does not run. Use `evaluate()` when the caller has loaded a resource and tenant/attribute isolation must be enforced.

## Isolation policies

`EvaluationPolicy.withIsolation('tenant_id')` compares the named attribute on the principal and resource. When they differ, evaluation is denied before permission rules run.

By default, a resource that omits the isolation attribute passes silently — appropriate for apps where only some resources are tenant-scoped. When every resource under a policy MUST carry the isolation attribute, opt into **strict mode**:

```typescript
// Chainable
EvaluationPolicy.withIsolation('tenant_id').strict();

// Or factory
EvaluationPolicy.withStrictIsolation('tenant_id');

// Or builder
EvaluationPolicy.builder().withIsolation('tenant_id').strict().build();
```

In strict mode, a `ResourceExtractor.attributes()` implementation that forgets to include `tenant_id` produces an isolation violation instead of a silent pass. Prefer strict mode when all resources handled by the policy are tenant-scoped — it converts a class of silent misconfigurations into loud denials.

## Scope filters

Five filter types, all AND'd together per action:

| Filter | JSON | What it does |
|--------|------|-------------|
| Same attribute | `{"match": "principal"}` | `resource.department == principal.department` |
| Cross attribute | `{"matchPrincipalAttribute": "userId"}` | `resource.owner == principal.userId` |
| Fallback chain | `{"matchPrincipalAttributes": ["userId", "email"]}` | Try each principal attr until one matches |
| Literal | `{"equals": "active"}` | `resource.status == "active"` |
| Set | `{"in": ["US", "EU"]}` | `resource.region` is in the list |

## Caching permission store

Permission lookups go to your database. You don't want that on every request. Wrap your loader with the built-in LRU+TTL cache:

```typescript
import { CachingPermissionStore } from '@ardley-technologies/acorn-core';

const store = new CachingPermissionStore(
  { load: async (key) => fetchFromDb(key[0], key[1]) },
  { ttlMs: 5 * 60_000, maxSize: 10_000 },
);
```

The cache is hand-rolled (~80 lines), zero dependencies. It does exactly what you'd expect and nothing else.

## You implement

- **`PrincipalExtractor`** — given a request context, return the authenticated principal (or undefined)
- **`ResourceExtractor`** — given a request context, find the resource ID, load it from your DB, return its attributes
- **`PermissionLoader`** — given a key like `['tenant-abc', 'editor']`, return the permission set JSON from your store

These are the only integration points. Everything else is handled.

## License

MIT
