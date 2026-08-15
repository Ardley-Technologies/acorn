# @ardley/acorn-fastify

Fastify plugin for Acorn. Uses Fastify's native route config system — authorization metadata lives right next to your schema definitions.

## Install

```bash
bun add @ardley/acorn-core @ardley/acorn-fastify
```

## Setup

```typescript
import Fastify from 'fastify';
import { acornPlugin, getAcornPrincipal, getAcornResource } from '@ardley/acorn-fastify';
import { defineActions, ActionRegistry, EvaluationPolicy } from '@ardley/acorn-core';

const Actions = defineActions({
  ListUsers: 'List all users',
  UpdateUser: 'Modify a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const app = Fastify();

await app.register(acornPlugin, {
  principalExtractor: myJwtExtractor,
  permissionStore: myStore,
  policy: EvaluationPolicy.withIsolation('tenant_id'),
  actionRegistry: registry,
});
```

## Protecting routes

Authorization config goes in Fastify's `config` option — the same place you'd put schema or other route metadata:

```typescript
app.get('/users', {
  config: { acorn: { actions: [Actions.ListUsers] } },
  handler: async (request, reply) => {
    const principal = getAcornPrincipal(request);
    return { tenant: principal.attribute('tenant_id') };
  },
});

app.put('/users/:id', {
  config: {
    acorn: {
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    },
  },
  handler: async (request, reply) => {
    const user = getAcornResource<User>(request, 'user');
    return user;
  },
});
```

Routes without `config.acorn` pass through untouched. The plugin only activates where you configure it.

### Gate vs. resource checks and isolation

`actions` runs a **gate check** — no resource is loaded, so `EvaluationPolicy.withIsolation(...)` does not run. If a route must be tenant-isolated by Acorn, declare it under `resources` with an extractor whose `attributes()` includes the isolation attribute. Gate-only routes rely on your principal loader alone — tenant-scoping for those must be enforced elsewhere.

## How it works internally

The plugin registers a `preHandler` hook that:

1. Checks if the route has `config.acorn` — exits immediately if not
2. Extracts principal → throws (Fastify catches, returns 401)
3. Loads permissions → throws 403
4. Runs gate checks → throws 403
5. Runs resource checks → throws 404/403
6. Stores results on request for handler access

Uses `fastify-plugin` to break Fastify's encapsulation boundary, so the hook applies to all routes regardless of where they're registered.

## License

MIT
