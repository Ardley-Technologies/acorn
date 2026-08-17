# @ardley/acorn-lambda

Acorn for AWS Lambda — API Gateway (payload 1.0 and 2.0), Function URLs, and ALB.

```bash
bun add @ardley/acorn-core @ardley/acorn-lambda
```

## Why this differs from the other adapters

The Express, Fastify and Koa adapters hook a middleware chain and either call
`next()` or throw for error middleware to catch. Lambda has neither: a handler
returns its response, and an uncaught throw becomes a 502 with no body you control.

So `protect()` here returns an **outcome** rather than middleware, and denials come
back as a response you return yourself:

```ts
const outcome = await guard(event);
if (!outcome.authorized) return outcome.response;
```

Nothing is thrown for an ordinary denial, so there is no error handler to wire up
and no way for a 403 to escape as a 502.

## Setup

```ts
import {
  ActionRegistry, EvaluationPolicy, CachingPermissionStore, defineActions,
} from '@ardley/acorn-core';
import { createAcornLambda } from '@ardley/acorn-lambda';

const Actions = defineActions({
  ListUsers: 'List all users',
  UpdateUser: 'Modify a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const acorn = createAcornLambda({
  principalExtractor,   // yours: verify the JWT, return a Principal
  permissionStore: new CachingPermissionStore(loader, { ttlMs: 30_000, maxSize: 500 }),
  policy: EvaluationPolicy.withIsolation('tenant_id').strict(),
  actionRegistry: registry,
});
```

## Gate check

```ts
const guard = acorn.protect({ actions: [Actions.ListUsers] });

export const handler = async (event) => {
  const outcome = await guard(event);
  if (!outcome.authorized) return outcome.response;

  const tenantId = outcome.principal.attribute('tenant_id');
  return { statusCode: 200, body: JSON.stringify(await listUsers(tenantId)) };
};
```

## Resource check

The extractor loads the resource once; the handler reads it back rather than
fetching it again.

```ts
const guard = acorn.protect({
  resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
});

export const handler = async (event) => {
  const outcome = await guard(event);
  if (!outcome.authorized) return outcome.response;

  const user = outcome.getResource<User>('user');
  return { statusCode: 200, body: JSON.stringify(user) };
};
```

## `withAuthorization` — the same thing, wrapped

For the common case where the whole handler sits behind one check:

```ts
export const handler = acorn.withAuthorization(
  { resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }] },
  async (event, { principal, getResource }) => {
    const user = getResource<User>('user');
    return { statusCode: 200, body: JSON.stringify(user) };
  },
);
```

The inner handler runs only when authorization passes.

## Responses

| Condition | Status |
|---|---|
| No principal extracted | 401 |
| No permission set for the role | 403 |
| Gate or resource check denied | 403 |
| Resource id absent, or `load()` returned null | 404 |

Body is `{"error": "..."}`. Override with `onError`:

```ts
createAcornLambda({
  ...,
  onError: (error) => ({
    statusCode: (error as { statusCode?: number }).statusCode ?? 403,
    headers: { 'content-type': 'application/problem+json' },
    body: JSON.stringify({ title: error.message }),
  }),
});
```

**Only Acorn's own denials reach `onError`.** A `TypeError` thrown from inside your
`ResourceExtractor` propagates out of `protect()` untouched, so a bug in a loader
surfaces as a 500 and reaches your logs instead of being flattened into a
misleading 403. The same applies to an unregistered action name, which is a
programming error, not a permission decision.

## Event compatibility

Both API Gateway payload formats work, plus Function URLs and ALB. The event types
are declared structurally, so this package has **no dependency on
`@types/aws-lambda`** — passing a real `APIGatewayProxyEventV2` just type-checks.

Two differences the adapter papers over:

- **Method and path** come from `requestContext.http` on payload 2.0 and from the
  top level on 1.0.
- **Header lookups are case-insensitive** in both, since 1.0 does not guarantee
  lowercased keys.

One it cannot:

- **Repeated query parameters are lossy on payload 2.0.** API Gateway joins repeats
  with commas and sends no multi-value map, so `?tag=a,b` and `?tag=a&tag=b` arrive
  identically and `queryParams('tag')` returns `['a', 'b']` for both. Payload 1.0
  carries `multiValueQueryStringParameters` and is exact. If a scope filter depends
  on values that may themselves contain commas, do not source it from a repeated
  v2 query parameter.

## Principal extractor

Nothing Lambda-specific, but note the permission key contract — `[tenantId, roleId]`,
in that order, when using `RepositoryPermissionLoader`:

```ts
const principalExtractor: PrincipalExtractor = {
  async extract(ctx) {
    const header = ctx.header('authorization');
    if (!header?.startsWith('Bearer ')) return undefined;

    const claims = await verify(header.slice(7));   // yours
    const attrs = Attributes.from({
      tenant_id: claims.tenant_id,
      role: claims.role,
      user_id: claims.sub,
    });

    return {
      attribute: (name) => attrs.attribute(name),
      permissionKey: () => [claims.tenant_id, claims.role],
    };
  },
};
```

Returning `undefined` produces a 401. Throwing produces a 500 — so return
`undefined` for "no credentials" and reserve throwing for "the token store is
broken".
