import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import fp from 'fastify-plugin';
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
  AuthenticationRequiredError,
  AuthorizationDeniedError,
  ResourceNotFoundError,
} from '@ardley-technologies/acorn-core';
import { FastifyRequestContext } from './request-context.js';

export interface RouteAuthConfig {
  actions?: ActionRef[];
  resources?: Array<{
    extractor: ResourceExtractor;
    actions: ActionRef[];
  }>;
}

export interface AcornFastifyOptions {
  principalExtractor: PrincipalExtractor;
  permissionStore: PermissionStore;
  policy: EvaluationPolicy;
  actionRegistry: ActionRegistry;
}

const PRINCIPAL_KEY = Symbol('acorn.principal');
const RESOURCES_KEY = Symbol('acorn.resources');

class AcornHttpError extends Error {
  statusCode: number;
  constructor(statusCode: number, message: string) {
    super(message);
    this.statusCode = statusCode;
  }
}

async function acornPluginImpl(fastify: FastifyInstance, options: AcornFastifyOptions): Promise<void> {
  const { principalExtractor, permissionStore, policy, actionRegistry } = options;

  fastify.addHook('preHandler', async (request: FastifyRequest, _reply: FastifyReply) => {
    const routeConfig = (request.routeOptions?.config as any)?.acorn as RouteAuthConfig | undefined;
    if (!routeConfig) return;

    const ctx = new FastifyRequestContext(request);

    // 1. Extract principal
    const principal = await principalExtractor.extract(ctx);
    if (!principal) {
      throw new AcornHttpError(401, 'Authentication required');
    }
    (request as any)[PRINCIPAL_KEY] = principal;

    // 2. Load permissions
    const permissions = await permissionStore.getPermissionSet(principal.permissionKey());
    if (!permissions) {
      throw new AcornHttpError(403, 'No permissions configured for this role');
    }

    // 3. Gate checks
    if (routeConfig.actions) {
      for (const actionRef of routeConfig.actions) {
        const name = resolveActionName(actionRef);
        const action = actionRegistry.resolve(name);
        const result = canPerformAction(permissions, action);
        if (result.permitted === false) {
          throw new AcornHttpError(403, result.reason!);
        }
      }
    }

    // 4. Resource checks
    if (routeConfig.resources) {
      const resources = new Map<string, unknown>();
      for (const { extractor, actions } of routeConfig.resources) {
        const resourceId = extractor.extractId(ctx);
        if (!resourceId) {
          throw new AcornHttpError(404, `${extractor.resourceType()} not found`);
        }

        const resource = await extractor.load(resourceId, principal);
        if (resource === null) {
          throw new AcornHttpError(404, `${extractor.resourceType()} "${resourceId}" not found`);
        }

        const resourceAttrs = extractor.attributes(resource);

        for (const actionRef of actions) {
          const name = resolveActionName(actionRef);
          const action = actionRegistry.resolve(name);
          const result = evaluate(permissions, principal, resourceAttrs, policy, action);
          if (result.permitted === false) {
            throw new AcornHttpError(403, result.reason!);
          }
        }

        resources.set(extractor.resourceType(), resource);
      }
      (request as any)[RESOURCES_KEY] = resources;
    }
  });
}

export const acornPlugin = fp(acornPluginImpl, { name: '@ardley-technologies/acorn-fastify' });

export function getAcornPrincipal(request: FastifyRequest): Principal {
  const principal = (request as any)[PRINCIPAL_KEY];
  if (!principal) throw new Error('No principal on request. Is acornPlugin registered and route configured?');
  return principal;
}

export function getAcornResource<R>(request: FastifyRequest, resourceType: string): R {
  const resources: Map<string, unknown> | undefined = (request as any)[RESOURCES_KEY];
  const resource = resources?.get(resourceType);
  if (resource === undefined) throw new Error(`No resource "${resourceType}" on request.`);
  return resource as R;
}
