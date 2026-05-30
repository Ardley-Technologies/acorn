export interface RoleRecord {
  readonly tenantId: string;
  readonly roleId: string;
  readonly roleName: string;
  readonly description: string;
  readonly systemRole: boolean;
  readonly assignableRoles: readonly string[];
  readonly configuration: string;
  readonly version: number;
}

export interface RoleManifest {
  readonly version: number;
  readonly roles: readonly RoleDefinition[];
}

export interface RoleDefinition {
  readonly roleId: string;
  readonly roleName: string;
  readonly description: string;
  readonly systemRole: boolean;
  readonly assignableRoles: readonly string[];
  readonly configuration: Record<string, unknown>;
}
