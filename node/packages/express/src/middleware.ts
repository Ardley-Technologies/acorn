import type { Request, Response, NextFunction, RequestHandler } from 'express';
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
import { ExpressRequestContext } from './request-context.js';

const PRINCIPAL_KEY = Symbol('acorn.principal');
const RESOURCES_KEY = Symbol('acorn.resources');

export interface RouteAuthConfig {
  actions?: ActionRef[];
  resources?: Array<{
    extractor: ResourceExtractor;
    actions: ActionRef[];
  }>;
}

export interface AcornExpressOptions {
  principalExtractor: PrincipalExtractor;
  permissionStore: PermissionStore;
  policy: EvaluationPolicy;
  actionRegistry: ActionRegistry;
  onError?: (err: Error, req: Request, res: Response, next: NextFunction) => void;
}

export interface AcornExpress {
  protect(config: RouteAuthConfig): RequestHandler;
  getPrincipal(req: Request): Principal;
  getResource<R>(req: Request, resourceType: string): R;
}

export function createAcorn(options: AcornExpressOptions): AcornExpress {
  const { principalExtractor, permissionStore, policy, actionRegistry, onError } = options;

  function handleError(err: Error, req: Request, res: Response, next: NextFunction): void {
    if (onError) {
      onError(err, req, res, next);
      return;
    }
    if (err instanceof AuthenticationRequiredError) {
      res.status(401).json({ error: err.message });
    } else if (err instanceof AuthorizationDeniedError) {
      res.status(403).json({ error: err.message });
    } else if (err instanceof ResourceNotFoundError) {
      res.status(404).json({ error: err.message });
    } else {
      next(err);
    }
  }

  function protect(config: RouteAuthConfig): RequestHandler {
    return async (req: Request, res: Response, next: NextFunction) => {
      try {
        const ctx = new ExpressRequestContext(req);

        // 1. Extract principal
        const principal = await principalExtractor.extract(ctx);
        if (!principal) {
          throw new AuthenticationRequiredError();
        }
        (req as any)[PRINCIPAL_KEY] = principal;

        // 2. Load permissions
        const permissions = await permissionStore.getPermissionSet(principal.permissionKey());
        if (!permissions) {
          throw AuthorizationDeniedError.noPermissions('No permissions configured for this role');
        }

        // 3. Gate checks
        if (config.actions) {
          for (const actionRef of config.actions) {
            const name = resolveActionName(actionRef);
            const action = actionRegistry.resolve(name);
            const result = canPerformAction(permissions, action);
            if (result.permitted === false) {
              throw AuthorizationDeniedError.gateCheck(name, result.reason!);
            }
          }
        }

        // 4. Resource checks
        if (config.resources) {
          const resources = getOrCreateResourceMap(req);
          for (const { extractor, actions } of config.resources) {
            const resourceId = extractor.extractId(ctx);
            if (!resourceId) {
              throw new ResourceNotFoundError(extractor.resourceType(), '(missing)');
            }

            const resource = await extractor.load(resourceId, principal);
            if (resource === null) {
              throw new ResourceNotFoundError(extractor.resourceType(), resourceId);
            }

            const resourceAttrs = extractor.attributes(resource);

            for (const actionRef of actions) {
              const name = resolveActionName(actionRef);
              const action = actionRegistry.resolve(name);
              const result = evaluate(permissions, principal, resourceAttrs, policy, action);
              if (result.permitted === false) {
                throw AuthorizationDeniedError.resourceCheck(
                  name, extractor.resourceType(), resourceId, result.reason!,
                );
              }
            }

            resources.set(extractor.resourceType(), resource);
          }
        }

        next();
      } catch (err) {
        handleError(err as Error, req, res, next);
      }
    };
  }

  function getPrincipal(req: Request): Principal {
    const principal = (req as any)[PRINCIPAL_KEY];
    if (!principal) {
      throw new Error('No principal on request. Was acorn.protect() middleware applied?');
    }
    return principal;
  }

  function getResource<R>(req: Request, resourceType: string): R {
    const resources: Map<string, unknown> | undefined = (req as any)[RESOURCES_KEY];
    const resource = resources?.get(resourceType);
    if (resource === undefined) {
      throw new Error(`No resource "${resourceType}" on request. Was it configured in acorn.protect()?`);
    }
    return resource as R;
  }

  return { protect, getPrincipal, getResource };
}

function getOrCreateResourceMap(req: Request): Map<string, unknown> {
  let map = (req as any)[RESOURCES_KEY] as Map<string, unknown> | undefined;
  if (!map) {
    map = new Map();
    (req as any)[RESOURCES_KEY] = map;
  }
  return map;
}
