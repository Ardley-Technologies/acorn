export class AuthenticationRequiredError extends Error {
  readonly statusCode = 401;

  constructor(message = 'Authentication required') {
    super(message);
    this.name = 'AuthenticationRequiredError';
  }
}

export type AuthorizationDeniedKind = 'gate' | 'resource' | 'noPermissions';

export class AuthorizationDeniedError extends Error {
  readonly statusCode = 403;
  readonly actionName?: string;
  readonly resourceType?: string;
  readonly resourceId?: string;
  readonly kind: AuthorizationDeniedKind;

  constructor(
    message: string,
    kind: AuthorizationDeniedKind,
    actionName?: string,
    resourceType?: string,
    resourceId?: string,
  ) {
    super(message);
    this.name = 'AuthorizationDeniedError';
    this.kind = kind;
    this.actionName = actionName;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  static noPermissions(message: string): AuthorizationDeniedError {
    return new AuthorizationDeniedError(message, 'noPermissions');
  }

  static gateCheck(actionName: string, reason: string): AuthorizationDeniedError {
    return new AuthorizationDeniedError(reason, 'gate', actionName);
  }

  static resourceCheck(actionName: string, resourceType: string, resourceId: string, reason: string): AuthorizationDeniedError {
    return new AuthorizationDeniedError(reason, 'resource', actionName, resourceType, resourceId);
  }
}

export class ResourceNotFoundError extends Error {
  readonly statusCode = 404;
  readonly resourceType: string;
  readonly resourceId: string;

  constructor(resourceType: string, resourceId: string) {
    super(`${resourceType} "${resourceId}" not found`);
    this.name = 'ResourceNotFoundError';
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }
}
