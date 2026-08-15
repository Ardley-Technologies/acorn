export interface AttributeSource {
  attribute(name: string): string | undefined;
}

export interface Principal extends AttributeSource {
  /**
   * Returns the lookup key used to fetch this principal's permission set
   * from a `PermissionStore`. The key shape is defined by the store: it is
   * a contract between your `Principal` implementation and your
   * `PermissionLoader`.
   *
   * When using `@ardley/acorn-roles`' `RepositoryPermissionLoader`, the
   * contract is `[tenantId, roleId]`, in that order. Role configurations
   * are per-tenant, so a single-element key (e.g. just `[roleId]`) would
   * cause tenant A's customized role config to be served to tenant B.
   * The loader guards against this by returning `undefined` for a
   * malformed key, which the framework adapter surfaces as
   * `AuthorizationDeniedError.noPermissions` (HTTP 403).
   *
   * `CachingPermissionStore` joins the key with `'::'` to form its cache
   * key. Keep the key shape stable across a deployment so the cache does
   * not collide.
   */
  permissionKey(): string[];
}

export interface Action {
  readonly name: string;
  readonly description: string;
}

export interface RequestContext {
  pathParam(name: string): string | undefined;
  queryParam(name: string): string | undefined;
  queryParams(name: string): string[];
  header(name: string): string | undefined;
  path(): string;
  method(): string;
}

export interface AuthorizationResult {
  readonly permitted: boolean;
  readonly reason?: string;
}

export type ActionRef = Action | string;

export function resolveActionName(ref: ActionRef): string {
  return typeof ref === 'string' ? ref : ref.name;
}
