import type {
  PrincipalExtractor,
  PermissionStore,
  ResourceExtractor,
  Principal,
  ActionRef,
  EvaluationPolicy,
  ActionRegistry,
} from '@ardley/acorn-core';
import {
  canPerformAction,
  evaluate,
  resolveActionName,
  AuthenticationRequiredError,
  AuthorizationDeniedError,
  ResourceNotFoundError,
} from '@ardley/acorn-core';
import type { LambdaHttpEvent, LambdaHttpResponse } from './event.js';
import { LambdaRequestContext } from './request-context.js';

export interface RouteAuthConfig {
  actions?: ActionRef[];
  resources?: Array<{
    extractor: ResourceExtractor;
    actions: ActionRef[];
  }>;
}

export interface AcornLambdaOptions {
  principalExtractor: PrincipalExtractor;
  permissionStore: PermissionStore;
  policy: EvaluationPolicy;
  actionRegistry: ActionRegistry;
  /**
   * Map a denial to a response. Defaults to 401 for authentication, 403 for
   * authorization, 404 for a missing resource, with a `{ "error": "..." }` body.
   *
   * Override to match an existing error envelope. Anything that is not one of
   * Acorn's three error types is rethrown rather than passed here — a bug in a
   * resource extractor should surface as a 500, not be flattened into a 403.
   */
  onError?: (error: Error) => LambdaHttpResponse;
}

/** Successful authorization, carrying the principal and any loaded resources. */
export interface AcornAuthorized {
  authorized: true;
  principal: Principal;
  /** A resource loaded by a configured extractor, by `resourceType()`. */
  getResource<R>(resourceType: string): R;
}

/** Denied authorization, carrying the response to return from the handler. */
export interface AcornDenied {
  authorized: false;
  response: LambdaHttpResponse;
  error: Error;
}

export type AcornOutcome = AcornAuthorized | AcornDenied;

export interface AcornLambda {
  /**
   * Run the configured checks against an event.
   *
   * Returns an outcome rather than throwing, because a Lambda handler returns
   * its own responses and has no error middleware to fall through to:
   *
   * ```ts
   * const outcome = await guard(event);
   * if (!outcome.authorized) return outcome.response;
   * ```
   */
  protect(config: RouteAuthConfig): (event: LambdaHttpEvent) => Promise<AcornOutcome>;

  /**
   * Wrap a handler so it only runs when authorization passes. The handler
   * receives the principal and any loaded resources as a second argument.
   */
  withAuthorization<E extends LambdaHttpEvent, R>(
    config: RouteAuthConfig,
    handler: (event: E, acorn: AcornAuthorized) => Promise<R>,
  ): (event: E) => Promise<R | LambdaHttpResponse>;
}

function defaultErrorResponse(error: Error): LambdaHttpResponse {
  // Each Acorn error carries its own `statusCode` (401/403/404), so read it
  // rather than re-deriving from the type. A new error type added upstream then
  // maps correctly here without a change.
  const status = (error as { statusCode?: number }).statusCode ?? 403;

  return {
    statusCode: status,
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ error: error.message }),
  };
}

function isAcornError(error: unknown): error is Error {
  return (
    error instanceof AuthenticationRequiredError ||
    error instanceof AuthorizationDeniedError ||
    error instanceof ResourceNotFoundError
  );
}

export function createAcornLambda(options: AcornLambdaOptions): AcornLambda {
  const { principalExtractor, permissionStore, policy, actionRegistry, onError } = options;
  const toResponse = onError ?? defaultErrorResponse;

  function protect(config: RouteAuthConfig): (event: LambdaHttpEvent) => Promise<AcornOutcome> {
    return async (event: LambdaHttpEvent): Promise<AcornOutcome> => {
      const resources = new Map<string, unknown>();

      try {
        const ctx = new LambdaRequestContext(event);

        // 1. Extract principal
        const principal = await principalExtractor.extract(ctx);
        if (!principal) {
          throw new AuthenticationRequiredError();
        }

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

        return {
          authorized: true,
          principal,
          getResource<R>(resourceType: string): R {
            const resource = resources.get(resourceType);
            if (resource === undefined) {
              throw new Error(
                `No resource "${resourceType}" was loaded. Was it configured in protect({ resources: [...] })?`,
              );
            }
            return resource as R;
          },
        };
      } catch (error) {
        // Only Acorn's own denials become responses. A thrown TypeError from an
        // extractor is a bug and must not be reported to the caller as a 403.
        if (!isAcornError(error)) throw error;
        return { authorized: false, response: toResponse(error), error };
      }
    };
  }

  function withAuthorization<E extends LambdaHttpEvent, R>(
    config: RouteAuthConfig,
    handler: (event: E, acorn: AcornAuthorized) => Promise<R>,
  ): (event: E) => Promise<R | LambdaHttpResponse> {
    const guard = protect(config);
    return async (event: E): Promise<R | LambdaHttpResponse> => {
      const outcome = await guard(event);
      if (!outcome.authorized) return outcome.response;
      return handler(event, outcome);
    };
  }

  return { protect, withAuthorization };
}
