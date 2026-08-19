import type { Context, Next, Middleware } from 'koa';
import type {
  PrincipalExtractor,
  PermissionStore,
  ResourceExtractor,
  Principal,
  ActionRef,
} from '@ardley-technologies/acorn-core';
import {
  EvaluationPolicy,
  ActionRegistry,
  canPerformAction,
  evaluate,
  resolveActionName,
} from '@ardley-technologies/acorn-core';
import { KoaRequestContext } from './request-context.js';

const PRINCIPAL_KEY = Symbol('acorn.principal');
const RESOURCES_KEY = Symbol('acorn.resources');

export interface RouteAuthConfig {
  actions?: ActionRef[];
  resources?: Array<{
    extractor: ResourceExtractor;
    actions: ActionRef[];
  }>;
}

export interface AcornKoaOptions {
  principalExtractor: PrincipalExtractor;
  permissionStore: PermissionStore;
  policy: EvaluationPolicy;
  actionRegistry: ActionRegistry;
}

export interface AcornKoa {
  protect(config: RouteAuthConfig): Middleware;
  getPrincipal(ctx: Context): Principal;
  getResource<R>(ctx: Context, resourceType: string): R;
}

export function createAcornKoa(options: AcornKoaOptions): AcornKoa {
  const { principalExtractor, permissionStore, policy, actionRegistry } = options;

  function protect(config: RouteAuthConfig): Middleware {
    return async (ctx: Context, next: Next) => {
      const requestContext = new KoaRequestContext(ctx);

      // 1. Extract principal
      const principal = await principalExtractor.extract(requestContext);
      if (!principal) {
        ctx.status = 401;
        ctx.body = { error: 'Authentication required' };
        return;
      }
      (ctx.state as any)[PRINCIPAL_KEY] = principal;

      // 2. Load permissions
      const permissions = await permissionStore.getPermissionSet(principal.permissionKey());
      if (!permissions) {
        ctx.status = 403;
        ctx.body = { error: 'No permissions configured for this role' };
        return;
      }

      // 3. Gate checks
      if (config.actions) {
        for (const actionRef of config.actions) {
          const name = resolveActionName(actionRef);
          const action = actionRegistry.resolve(name);
          const result = canPerformAction(permissions, action);
          if (result.permitted === false) {
            ctx.status = 403;
            ctx.body = { error: result.reason };
            return;
          }
        }
      }

      // 4. Resource checks
      if (config.resources) {
        const resources = getOrCreateResourceMap(ctx);
        for (const { extractor, actions } of config.resources) {
          const resourceId = extractor.extractId(requestContext);
          if (!resourceId) {
            ctx.status = 404;
            ctx.body = { error: `${extractor.resourceType()} not found` };
            return;
          }

          const resource = await extractor.load(resourceId, principal);
          if (resource === null) {
            ctx.status = 404;
            ctx.body = { error: `${extractor.resourceType()} "${resourceId}" not found` };
            return;
          }

          const resourceAttrs = extractor.attributes(resource);

          for (const actionRef of actions) {
            const name = resolveActionName(actionRef);
            const action = actionRegistry.resolve(name);
            const result = evaluate(permissions, principal, resourceAttrs, policy, action);
            if (result.permitted === false) {
              ctx.status = 403;
              ctx.body = { error: result.reason };
              return;
            }
          }

          resources.set(extractor.resourceType(), resource);
        }
      }

      await next();
    };
  }

  function getPrincipal(ctx: Context): Principal {
    const principal = (ctx.state as any)[PRINCIPAL_KEY];
    if (!principal) {
      throw new Error('No principal on context. Was acorn.protect() middleware applied?');
    }
    return principal;
  }

  function getResource<R>(ctx: Context, resourceType: string): R {
    const resources: Map<string, unknown> | undefined = (ctx.state as any)[RESOURCES_KEY];
    const resource = resources?.get(resourceType);
    if (resource === undefined) {
      throw new Error(`No resource "${resourceType}" on context.`);
    }
    return resource as R;
  }

  return { protect, getPrincipal, getResource };
}

function getOrCreateResourceMap(ctx: Context): Map<string, unknown> {
  let map = (ctx.state as any)[RESOURCES_KEY] as Map<string, unknown> | undefined;
  if (!map) {
    map = new Map();
    (ctx.state as any)[RESOURCES_KEY] = map;
  }
  return map;
}
