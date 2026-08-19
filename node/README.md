# Acorn for Node.js

*Declarative, schema-free RBAC for Node.js APIs.*

---

Most Node authorization ends up as scattered `if (user.role === 'admin')` checks inside handlers. You forget one, and you've shipped an open endpoint. Or you build a middleware that checks roles, but it's binary — you can't express "editors can update users, but only in their own department."

Acorn takes a different approach. Authorization is declared at the route level — on your middleware config — and evaluated before your handler code ever executes. If a request doesn't have permission, your handler never sees it.

Permissions are plain JSON. No policy language, no external service, no DSL. Evaluation happens in-process in microseconds. Your existing JSON tooling works. And TypeScript's type system catches mistakes at compile time, not in production.

## Packages

| Package | What it does |
|---------|-------------|
| `@ardley-technologies/acorn-core` | Evaluation engine, permission model, scope filters, caching. Zero runtime deps. |
| `@ardley-technologies/acorn-express` | Express middleware |
| `@ardley-technologies/acorn-fastify` | Fastify plugin |
| `@ardley-technologies/acorn-koa` | Koa middleware |
| `@ardley-technologies/acorn-lambda` | AWS Lambda adapter (API Gateway, Function URLs, ALB) |
| `@ardley-technologies/acorn-roles` | Role management and seeding for multi-tenant apps |

Pick the packages that match your stack. Use the core directly if your framework isn't listed — it has no opinions about HTTP.

## What it looks like

```typescript
import { createAcorn } from '@ardley-technologies/acorn-express';
import { defineActions, ActionRegistry, EvaluationPolicy } from '@ardley-technologies/acorn-core';

const Actions = defineActions({
  ListUsers: 'List all users',
  UpdateUser: 'Modify a user',
  DeleteUser: 'Permanently remove a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const acorn = createAcorn({
  principalExtractor: myJwtExtractor,
  permissionStore: myCachingStore,
  policy: EvaluationPolicy.withIsolation('tenant_id'),
  actionRegistry: registry,
});

// Gate check — must have the action, no resource needed
app.get('/users', acorn.protect({ actions: [Actions.ListUsers] }), handler);

// Resource check — loads the user, evaluates scoped permissions against it
app.put('/users/:id', acorn.protect({
  resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
}), (req, res) => {
  // Only reaches here if authorized. User already loaded.
  const user = acorn.getResource<User>(req, 'user');
});
```

No role strings in your code. No manual permission checks in your handlers. The route config *is* your authorization policy.

## Permissions are plain JSON

```json
{
  "allow": {
    "ListUsers": "all",
    "UpdateUser": { "department": { "match": "principal" } },
    "ViewReports": { "region": { "in": ["US", "EU"] } }
  },
  "deny": {
    "DeleteUser": "all"
  }
}
```

That says: can list all users, can update users in their own department, can view reports for US and EU, cannot delete anyone ever. The attribute names are yours — Acorn doesn't know what "department" means. It just compares values.

Superadmin shorthand: `{"allow": "all"}`

## Deny wins

If a permission set has both an allow and a deny for the same action, deny wins. Always. This isn't configurable because it shouldn't be.

## You own everything

- **You own the principal.** Implement one function that extracts identity from a request. JWT, session cookie, API key — your call.
- **You own the resource.** Implement one object per resource type that knows how to load it and expose its attributes.
- **You own the storage.** Permission sets live wherever you want. Implement a loader, wrap it with the built-in caching store. Done.

## Development

```bash
bun install
bun test
```

82 tests, runs in ~350ms.

## License

MIT
