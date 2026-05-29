import type { AttributeSource, Action, AuthorizationResult } from './types.js';
import type { PermissionLevel } from './permission-level.js';
import type { PermissionSet } from './permission-set.js';
import type { EvaluationPolicy } from './evaluation-policy.js';

const ALLOWED: AuthorizationResult = Object.freeze({ permitted: true });

function denied(reason: string): AuthorizationResult {
  return { permitted: false, reason };
}

export function canPerformAction(permissions: PermissionSet, action: Action): AuthorizationResult {
  const name = action.name;

  if (permissions.hasUnconditionalDeny(name)) {
    return denied(`Action "${name}" is explicitly denied`);
  }

  if (permissions.hasAllowFor(name)) {
    return ALLOWED;
  }

  return denied(`Action "${name}" is not permitted for this role`);
}

export function evaluate(
  permissions: PermissionSet,
  principal: AttributeSource,
  resource: AttributeSource,
  policy: EvaluationPolicy,
  action: Action,
): AuthorizationResult {
  const name = action.name;

  // 1. Isolation
  const violation = policy.checkIsolation(principal, resource);
  if (violation !== undefined) {
    return denied(violation);
  }

  // 2. Unconditional deny
  if (permissions.hasUnconditionalDeny(name)) {
    return denied(`Action "${name}" is unconditionally denied`);
  }

  // 3. Scoped deny
  const denyLevel = permissions.denyLevel(name);
  if (denyLevel !== undefined && scopeMatches(denyLevel, principal, resource)) {
    return denied(`Action "${name}" denied (scope matched)`);
  }

  // 4. Allow all
  if (permissions.isAllowAll()) {
    return ALLOWED;
  }

  // 5-6. Allow
  const allowLevel = permissions.allowLevel(name);
  if (allowLevel === undefined) {
    return denied(`Action "${name}" is not permitted for this role`);
  }

  if (allowLevel.type === 'all') {
    return ALLOWED;
  }

  if (allowLevel.type === 'scoped') {
    if (allowLevel.filter.matches(principal, resource)) {
      return ALLOWED;
    }
    return denied(`Action "${name}" scope filter did not match`);
  }

  return denied(`Action "${name}" is not permitted for this role`);
}

function scopeMatches(level: PermissionLevel, principal: AttributeSource, resource: AttributeSource): boolean {
  if (level.type === 'all') return true;
  if (level.type === 'scoped') {
    return level.filter.matches(principal, resource);
  }
  return false;
}
