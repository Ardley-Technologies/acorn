import { describe, test, expect } from 'bun:test';
import {
  ActionRegistry,
  Attributes,
  EvaluationPolicy,
  PermissionSet,
  defineActions,
} from '@ardley/acorn-core';
import type {
  AttributeSource,
  PermissionLoader,
  PermissionStore,
  Principal,
  PrincipalExtractor,
  RequestContext,
  ResourceExtractor,
} from '@ardley/acorn-core';
import { createAcornLambda } from '../adapter.js';
import type { LambdaHttpEventV2 } from '../event.js';

const Actions = defineActions({
  ListUsers: 'List users',
  UpdateUser: 'Update a user',
  DeleteUser: 'Delete a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

interface User {
  id: string;
  tenant_id?: string;
}

const USERS: Record<string, User> = {
  'u-same': { id: 'u-same', tenant_id: 'tenant-a' },
  'u-other': { id: 'u-other', tenant_id: 'tenant-b' },
  'u-notenant': { id: 'u-notenant' },
};

const userExtractor: ResourceExtractor<User> = {
  resourceType: () => 'user',
  extractId: (ctx: RequestContext) => ctx.pathParam('id'),
  load: async (id: string) => USERS[id] ?? null,
  attributes: (u: User) => Attributes.from({ id: u.id, tenant_id: u.tenant_id }),
};

function principal(tenant: string | undefined, role: string): Principal {
  const attrs = Attributes.from({ tenant_id: tenant, role });
  return {
    attribute: (n: string) => attrs.attribute(n),
    permissionKey: () => [tenant ?? '', role],
  };
}

function extractor(p: Principal | undefined): PrincipalExtractor {
  return { extract: async () => p };
}

function store(sets: Record<string, PermissionSet>): PermissionStore {
  const loader: PermissionLoader = {
    load: async (key: string[]) => sets[key.join('::')],
  };
  return {
    getPermissionSet: (key: string[]) => loader.load(key),
    invalidate: () => {},
  };
}

const event = (id = 'u-same'): LambdaHttpEventV2 => ({
  version: '2.0',
  rawPath: `/users/${id}`,
  pathParameters: { id },
  requestContext: { http: { method: 'GET', path: `/users/${id}` } },
});

const ADMIN = PermissionSet.fromObject({
  allow: { ListUsers: 'all', UpdateUser: 'all' },
  deny: { DeleteUser: 'all' },
});

function build(p: Principal | undefined, policy = EvaluationPolicy.withIsolation('tenant_id')) {
  return createAcornLambda({
    principalExtractor: extractor(p),
    permissionStore: store({ 'tenant-a::admin': ADMIN }),
    policy,
    actionRegistry: registry,
  });
}

const body = (r: { body: string }) => JSON.parse(r.body) as { error: string };

describe('createAcornLambda — authentication', () => {
  test('401 when no principal is extracted', async () => {
    const guard = build(undefined).protect({ actions: [Actions.ListUsers] });
    const outcome = await guard(event());
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(401);
    expect(body(outcome.response).error).toBe('Authentication required');
  });

  test('403 when the role has no permission set', async () => {
    const guard = build(principal('tenant-a', 'ghost')).protect({ actions: [Actions.ListUsers] });
    const outcome = await guard(event());
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(403);
  });
});

describe('createAcornLambda — gate checks', () => {
  test('allows a permitted action', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({ actions: [Actions.ListUsers] });
    const outcome = await guard(event());
    expect(outcome.authorized).toBe(true);
    if (!outcome.authorized) return;
    expect(outcome.principal.attribute('role')).toBe('admin');
  });

  test('denies an unconditionally denied action', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({ actions: [Actions.DeleteUser] });
    const outcome = await guard(event());
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(403);
  });
});

describe('createAcornLambda — resource checks', () => {
  test('allows same-tenant access and exposes the loaded resource', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    });
    const outcome = await guard(event('u-same'));
    expect(outcome.authorized).toBe(true);
    if (!outcome.authorized) return;
    expect(outcome.getResource<User>('user').id).toBe('u-same');
  });

  test('denies cross-tenant access', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    });
    const outcome = await guard(event('u-other'));
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(403);
    expect(body(outcome.response).error).toContain('Isolation violation');
  });

  test('404 when the resource does not exist', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    });
    const outcome = await guard(event('missing'));
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(404);
  });

  test('404 when the id is absent from the event', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    });
    const outcome = await guard({ version: '2.0', rawPath: '/users', requestContext: { http: { method: 'GET', path: '/users' } } });
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(404);
  });

  test('strict isolation denies a resource missing the isolation attribute', async () => {
    const guard = build(
      principal('tenant-a', 'admin'),
      EvaluationPolicy.withIsolation('tenant_id').strict(),
    ).protect({ resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }] });
    const outcome = await guard(event('u-notenant'));
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(body(outcome.response).error).toContain('strict mode');
  });

  test('lenient isolation allows the same resource', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({
      resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
    });
    expect((await guard(event('u-notenant'))).authorized).toBe(true);
  });

  test('getResource throws for a type that was never configured', async () => {
    const guard = build(principal('tenant-a', 'admin')).protect({ actions: [Actions.ListUsers] });
    const outcome = await guard(event());
    if (!outcome.authorized) throw new Error('expected authorized');
    expect(() => outcome.getResource('user')).toThrow(/No resource "user" was loaded/);
  });
});

describe('createAcornLambda — error handling', () => {
  test('onError overrides the response envelope', async () => {
    const acorn = createAcornLambda({
      principalExtractor: extractor(undefined),
      permissionStore: store({}),
      policy: EvaluationPolicy.none(),
      actionRegistry: registry,
      onError: (e) => ({ statusCode: 418, body: JSON.stringify({ detail: e.message }) }),
    });
    const outcome = await acorn.protect({ actions: [Actions.ListUsers] })(event());
    expect(outcome.authorized).toBe(false);
    if (outcome.authorized) return;
    expect(outcome.response.statusCode).toBe(418);
  });

  test('a non-Acorn error from an extractor propagates instead of becoming a 403', async () => {
    const exploding: ResourceExtractor<User> = {
      ...userExtractor,
      load: async () => {
        throw new TypeError('bug in extractor');
      },
    };
    const acorn = createAcornLambda({
      principalExtractor: extractor(principal('tenant-a', 'admin')),
      permissionStore: store({ 'tenant-a::admin': ADMIN }),
      policy: EvaluationPolicy.withIsolation('tenant_id'),
      actionRegistry: registry,
    });
    const guard = acorn.protect({ resources: [{ extractor: exploding, actions: [Actions.UpdateUser] }] });
    await expect(guard(event())).rejects.toThrow('bug in extractor');
  });

  test('an unknown action name propagates as a programming error', async () => {
    const acorn = build(principal('tenant-a', 'admin'));
    await expect(acorn.protect({ actions: ['NotRegistered'] })(event())).rejects.toThrow(/Unknown action/);
  });
});

describe('withAuthorization', () => {
  test('runs the handler when authorized and passes the principal', async () => {
    const acorn = build(principal('tenant-a', 'admin'));
    const handler = acorn.withAuthorization(
      { actions: [Actions.ListUsers] },
      async (_e, ctx) => ({ statusCode: 200, body: JSON.stringify({ role: ctx.principal.attribute('role') }) }),
    );
    const res = await handler(event());
    expect(res.statusCode).toBe(200);
    expect(body(res).error).toBeUndefined();
    expect(JSON.parse(res.body).role).toBe('admin');
  });

  test('short-circuits with the denial response and never calls the handler', async () => {
    const acorn = build(undefined);
    let called = false;
    const handler = acorn.withAuthorization({ actions: [Actions.ListUsers] }, async () => {
      called = true;
      return { statusCode: 200, body: '{}' };
    });
    const res = await handler(event());
    expect(res.statusCode).toBe(401);
    expect(called).toBe(false);
  });

  test('exposes loaded resources to the handler', async () => {
    const acorn = build(principal('tenant-a', 'admin'));
    const handler = acorn.withAuthorization(
      { resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }] },
      async (_e, ctx) => ({ statusCode: 200, body: JSON.stringify(ctx.getResource<User>('user')) }),
    );
    const res = await handler(event('u-same'));
    expect(JSON.parse(res.body).id).toBe('u-same');
  });
});
