import { describe, test, expect } from 'bun:test';
import Fastify from 'fastify';
import {
  Attributes,
  PermissionSet,
  EvaluationPolicy,
  ActionRegistry,
  defineActions,
} from '@ardley-technologies/acorn-core';
import type { Principal, PrincipalExtractor, PermissionStore, ResourceExtractor, RequestContext } from '@ardley-technologies/acorn-core';
import { acornPlugin, getAcornPrincipal, getAcornResource } from '../plugin.js';

const Actions = defineActions({
  ListUsers: 'List users',
  UpdateUser: 'Update a user',
  DeleteUser: 'Delete a user',
});

const registry = new ActionRegistry();
registry.registerAll(Actions);

const policy = EvaluationPolicy.withIsolation('tenant_id');

function makePrincipal(attrs: Record<string, string>, key: string[]): Principal {
  const source = Attributes.from(attrs);
  return {
    attribute: (name: string) => source.attribute(name),
    permissionKey: () => key,
  };
}

const testPrincipal = makePrincipal({ tenant_id: 't-1', department: 'Eng' }, ['t-1', 'editor']);

const permissionData: Record<string, PermissionSet> = {
  't-1::editor': PermissionSet.fromJson(JSON.stringify({
    allow: {
      ListUsers: 'all',
      UpdateUser: { department: { match: 'principal' } },
    },
    deny: { DeleteUser: 'all' },
  })),
};

const principalExtractor: PrincipalExtractor = {
  async extract(ctx: RequestContext) {
    const token = ctx.header('authorization');
    if (token === 'Bearer valid') return testPrincipal;
    return undefined;
  },
};

const permissionStore: PermissionStore = {
  async getPermissionSet(key: string[]) {
    return permissionData[key.join('::')];
  },
  invalidate() {},
};

const userExtractor: ResourceExtractor<{ id: string; tenant_id: string; department: string }> = {
  resourceType: () => 'user',
  extractId: (ctx: RequestContext) => ctx.pathParam('id'),
  async load(id) {
    if (id === 'u-404') return null;
    return { id, tenant_id: 't-1', department: id === 'u-eng' ? 'Eng' : 'Sales' };
  },
  attributes: (user) => Attributes.from({ tenant_id: user.tenant_id, department: user.department }),
};

async function buildApp() {
  const app = Fastify();
  await app.register(acornPlugin, {
    principalExtractor,
    permissionStore,
    policy,
    actionRegistry: registry,
  });

  app.get('/users', {
    config: { acorn: { actions: [Actions.ListUsers] } },
    handler: async (request, reply) => {
      const principal = getAcornPrincipal(request);
      return { tenant: principal.attribute('tenant_id') };
    },
  } as any);

  app.put('/users/:id', {
    config: { acorn: { resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }] } },
    handler: async (request, reply) => {
      const user = getAcornResource(request, 'user');
      return user;
    },
  } as any);

  app.delete('/users/:id', {
    config: { acorn: { actions: [Actions.DeleteUser] } },
    handler: async () => ({ ok: true }),
  } as any);

  return app;
}

describe('Fastify plugin', () => {
  test('401 when no auth header', async () => {
    const app = await buildApp();
    const res = await app.inject({ method: 'GET', url: '/users' });
    expect(res.statusCode).toBe(401);
  });

  test('200 on valid gate check', async () => {
    const app = await buildApp();
    const res = await app.inject({
      method: 'GET',
      url: '/users',
      headers: { authorization: 'Bearer valid' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().tenant).toBe('t-1');
  });

  test('403 on denied gate check', async () => {
    const app = await buildApp();
    const res = await app.inject({
      method: 'DELETE',
      url: '/users/u-eng',
      headers: { authorization: 'Bearer valid' },
    });
    expect(res.statusCode).toBe(403);
  });

  test('200 on matching resource scope', async () => {
    const app = await buildApp();
    const res = await app.inject({
      method: 'PUT',
      url: '/users/u-eng',
      headers: { authorization: 'Bearer valid' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().department).toBe('Eng');
  });

  test('403 on non-matching resource scope', async () => {
    const app = await buildApp();
    const res = await app.inject({
      method: 'PUT',
      url: '/users/u-sales',
      headers: { authorization: 'Bearer valid' },
    });
    expect(res.statusCode).toBe(403);
  });

  test('404 on missing resource', async () => {
    const app = await buildApp();
    const res = await app.inject({
      method: 'PUT',
      url: '/users/u-404',
      headers: { authorization: 'Bearer valid' },
    });
    expect(res.statusCode).toBe(404);
  });
});
