export interface AttributeSource {
  attribute(name: string): string | undefined;
}

export interface Principal extends AttributeSource {
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
