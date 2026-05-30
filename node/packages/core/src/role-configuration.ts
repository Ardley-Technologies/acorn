import { PermissionSet } from './permission-set.js';

export interface RoleConfiguration {
  readonly roleId: string;
  readonly roleName: string;
  readonly description?: string;
  readonly permissionJson: string;
  readonly systemRole: boolean;
  readonly assignableRoles: readonly string[];
  readonly createdAt?: Date;
  readonly updatedAt?: Date;
}

export function toPermissionSet(config: RoleConfiguration): PermissionSet {
  return PermissionSet.fromJson(config.permissionJson);
}

export function canAssign(config: RoleConfiguration, targetRoleId: string): boolean {
  return config.assignableRoles.includes(targetRoleId);
}

export interface HierarchyRule {
  readonly higherAction: string;
  readonly lowerAction: string;
}

export interface ValidationResult {
  readonly errors: readonly string[];
  readonly valid: boolean;
}

export class RoleConfigurationValidator {
  private constructor(private readonly hierarchyRules: readonly HierarchyRule[]) {}

  static builder(): RoleConfigurationValidatorBuilder {
    return new RoleConfigurationValidatorBuilder();
  }

  validate(permissionJson: string): ValidationResult {
    const errors: string[] = [];

    let permissionSet: PermissionSet;
    try {
      permissionSet = PermissionSet.fromJson(permissionJson);
    } catch (e) {
      errors.push(`Invalid permission JSON: ${(e as Error).message}`);
      return { errors, valid: false };
    }

    for (const rule of this.hierarchyRules) {
      const higher = permissionSet.allowLevel(rule.higherAction);
      const lower = permissionSet.allowLevel(rule.lowerAction);

      if (higher === undefined) continue;

      if (higher.type === 'all' && (lower === undefined || lower.type === 'none')) {
        errors.push(
          `Hierarchy violation: "${rule.higherAction}" is granted at 'all' but "${rule.lowerAction}" is not granted. ` +
          `Granting edit access requires at least equivalent view access.`
        );
      }
    }

    return { errors, valid: errors.length === 0 };
  }
}

export class RoleConfigurationValidatorBuilder {
  private rules: HierarchyRule[] = [];

  withHierarchy(higherAction: string, lowerAction: string): this {
    this.rules.push({ higherAction, lowerAction });
    return this;
  }

  build(): RoleConfigurationValidator {
    return new (RoleConfigurationValidator as unknown as {
      new (rules: readonly HierarchyRule[]): RoleConfigurationValidator;
    })(Object.freeze([...this.rules]));
  }
}
