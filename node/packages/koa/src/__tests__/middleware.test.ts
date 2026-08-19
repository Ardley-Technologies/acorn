import { describe, test, expect } from 'bun:test';
import Koa from 'koa';
import Router from '@koa/router';
import {
  Attributes,
  PermissionSet,
  EvaluationPolicy,
  ActionRegistry,
  defineActions,
} from '@ardley-technologies/acorn-core';
import type { Principal, PrincipalExtractor, PermissionStore, ResourceExtractor, RequestContext } from '@ardley-technologies/acorn-core';
import { createAcornKoa } from '../middleware.js';

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

function buildApp() {
  const acorn = createAcornKoa({
    principalExtractor,
    permissionStore,
    policy,
    actionRegistry: registry,
  });

  const router = new Router();

  router.get('/users', acorn.protect({ actions: [Actions.ListUsers] }), async (ctx) => {
    const principal = acorn.getPrincipal(ctx);
    ctx.body = { tenant: principal.attribute('tenant_id') };
  });

  router.put('/users/:id', acorn.protect({
    resources: [{ extractor: userExtractor, actions: [Actions.UpdateUser] }],
  }), async (ctx) => {
    const user = acorn.getResource(ctx, 'user');
    ctx.body = user;
  });

  router.delete('/users/:id', acorn.protect({
    actions: [Actions.DeleteUser],
  }), async (ctx) => {
    ctx.status = 204;
  });

  const app = new Koa();
  app.use(router.routes());
  app.use(router.allowedMethods());

  return app;
}

async function serve(app: Koa) {
  return new Promise<{ port: number; close: () => void }>((resolve) => {
    const server = app.listen(0, () => {
      const addr = server.address() as { port: number };
      resolve({ port: addr.port, close: () => server.close() });
    });
  });
}

describe('Koa middleware', () => {
  test('401 when no auth header', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users`);
      expect(res.status).toBe(401);
    } finally {
      close();
    }
  });

  test('200 on valid gate check', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users`, {
        headers: { authorization: 'Bearer valid' },
      });
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.tenant).toBe('t-1');
    } finally {
      close();
    }
  });

  test('403 on denied gate check', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users/u-eng`, {
        method: 'DELETE',
        headers: { authorization: 'Bearer valid' },
      });
      expect(res.status).toBe(403);
    } finally {
      close();
    }
  });

  test('200 on matching resource scope', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users/u-eng`, {
        method: 'PUT',
        headers: { authorization: 'Bearer valid' },
      });
      expect(res.status).toBe(200);
      const body = await res.json();
      expect(body.department).toBe('Eng');
    } finally {
      close();
    }
  });

  test('403 on non-matching resource scope', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users/u-sales`, {
        method: 'PUT',
        headers: { authorization: 'Bearer valid' },
      });
      expect(res.status).toBe(403);
    } finally {
      close();
    }
  });

  test('404 on missing resource', async () => {
    const app = buildApp();
    const { port, close } = await serve(app);
    try {
      const res = await fetch(`http://localhost:${port}/users/u-404`, {
        method: 'PUT',
        headers: { authorization: 'Bearer valid' },
      });
      expect(res.status).toBe(404);
    } finally {
      close();
    }
  });
});
