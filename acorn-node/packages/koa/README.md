# @ardley/acorn-koa

Koa middleware for Acorn. Same factory pattern as the Express adapter — call `createAcornKoa()`, get back a `protect()` middleware you compose with your router.

## Install

```bash
bun add @ardley/acorn-core @ardley/acorn-koa
```

## Usage

```typescript
import Koa from 'koa';
import Router from '@koa/router';
import { createAcornKoa } from '@ardley/acorn-koa';
import { defineActions, ActionRegistry, EvaluationPolicy } from '@ardley/acorn-core';

const Actions = defineActions({
  ListUsers: 'List all users',
  UpdateUser: 'Modify a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const acorn = createAcornKoa({
  principalExtractor: myJwtExtractor,
  permissionStore: myStore,
  policy: EvaluationPolicy.withIsolation('tenant_id'),
  actionRegistry: registry,
});

const router = new Router();

router.get('/users', acorn.protect({ actions: [Actions.ListUsers] }), async (ctx) => {
  const principal = acorn.getPrincipal(ctx);
  ctx.body = await listUsers(principal.attribute('tenant_id'));
});

router.put('/users/:id', acorn.protect({
  resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
}), async (ctx) => {
  const user = acorn.getResource<User>(ctx, 'user');
  ctx.body = user;
});

const app = new Koa();
app.use(router.routes());
```

## Behavior

The `protect()` middleware sets `ctx.status` and `ctx.body` directly on failure, then returns without calling `next()`. Your handler never fires.

- 401 if no principal
- 403 if no permissions or action denied
- 404 if resource missing

On success, it stores the principal and resources in `ctx.state` (Symbol-keyed to avoid collisions with your application state) and calls `next()`.

## License

MIT
