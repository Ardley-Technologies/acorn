# @ardley-technologies/acorn-express

Express middleware for Acorn. Wraps the core evaluation engine into a `protect()` middleware you apply per-route.

## Install

```bash
bun add @ardley-technologies/acorn-core @ardley-technologies/acorn-express
```

## Basic setup

```typescript
import express from 'express';
import { createAcorn } from '@ardley-technologies/acorn-express';
import { defineActions, ActionRegistry, EvaluationPolicy, CachingPermissionStore } from '@ardley-technologies/acorn-core';

const Actions = defineActions({
  ListUsers: 'List all users',
  UpdateUser: 'Modify a user',
  DeleteUser: 'Permanently remove a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const acorn = createAcorn({
  principalExtractor: myJwtExtractor,
  permissionStore: myStore,
  policy: EvaluationPolicy.withIsolation('tenant_id'),
  actionRegistry: registry,
});
```

## Protecting routes

```typescript
// Gate check — "does this user have ListUsers at all?"
app.get('/users', acorn.protect({ actions: [Actions.ListUsers] }), (req, res) => {
  const principal = acorn.getPrincipal(req);
  // ...
});

// Resource check — "can this user UpdateUser on THIS specific user?"
app.put('/users/:id', acorn.protect({
  resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
}), (req, res) => {
  // The user is already loaded. No second DB fetch.
  const user = acorn.getResource<User>(req, 'user');
  // ...
});
```

Routes without `acorn.protect()` are unprotected — Acorn only enforces where you tell it to.

### Gate vs. resource checks and isolation

`actions` runs a **gate check**: does this role hold the action at all? No resource is loaded, so `EvaluationPolicy.withIsolation('tenant_id')` has nothing to compare against and does not run. Gate-only routes rely on your principal loader and permission set alone — Acorn does not perform any tenant comparison for them.

`resources` runs a **resource check**: the resource is loaded and `EvaluationPolicy` is consulted, including any isolation attributes. Cross-tenant access is rejected before permission evaluation.

If a route must be tenant-isolated by Acorn, declare it under `resources` with an extractor whose `attributes()` includes the isolation attribute. Routes with no resource (e.g. `GET /logs/schema`) can safely use `actions` — but their tenant-scoping must be enforced elsewhere (e.g. by a partition key derived from the principal).

## What happens on failure

| Situation | Response |
|-----------|----------|
| No principal (bad/missing token) | 401 |
| No permission set found for role | 403 |
| Action denied (gate or resource) | 403 |
| Resource ID not in request | 404 |
| Resource not found in database | 404 |

Default behavior sends JSON error bodies. Override with `onError`:

```typescript
const acorn = createAcorn({
  ...options,
  onError: (err, req, res, next) => {
    // err is AuthenticationRequiredError, AuthorizationDeniedError, or ResourceNotFoundError
    // Handle however you want
  },
});
```

## Getting the principal and resources downstream

After authorization passes, the principal and any loaded resources are stored on the request (Symbol-keyed, no collisions):

```typescript
const principal = acorn.getPrincipal(req);        // throws if not authorized yet
const user = acorn.getResource<User>(req, 'user'); // throws if resource not loaded
```

## License

MIT
