export type {
  AttributeSource,
  Principal,
  Action,
  RequestContext,
  AuthorizationResult,
  ActionRef,
} from './types.js';
export { resolveActionName } from './types.js';

export { Attributes } from './attributes.js';

export type { AttributeFilter } from './attribute-filter.js';
export { matchesFilter, parseAttributeFilter } from './attribute-filter.js';

export { ScopeFilter } from './scope-filter.js';

export type { PermissionLevel } from './permission-level.js';
export { parsePermissionLevel } from './permission-level.js';

export { PermissionSet } from './permission-set.js';

export { EvaluationPolicy } from './evaluation-policy.js';

export { canPerformAction, evaluate } from './evaluator.js';

export { defineActions } from './define-actions.js';
export { ActionRegistry } from './action-registry.js';

export type { PermissionLoader, PermissionStore } from './permission-store.js';
export type { CachingStoreOptions } from './caching-store.js';
export { CachingPermissionStore } from './caching-store.js';

export type { ResourceExtractor } from './resource-extractor.js';
export type { PrincipalExtractor } from './principal-extractor.js';

export type {
  RoleConfiguration,
  HierarchyRule,
  ValidationResult,
} from './role-configuration.js';
export {
  toPermissionSet,
  canAssign,
  RoleConfigurationValidator,
  RoleConfigurationValidatorBuilder,
} from './role-configuration.js';

export {
  AuthenticationRequiredError,
  AuthorizationDeniedError,
  ResourceNotFoundError,
} from './errors.js';
export type { AuthorizationDeniedKind } from './errors.js';
