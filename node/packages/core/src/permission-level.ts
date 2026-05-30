import { ScopeFilter } from './scope-filter.js';

export type PermissionLevel =
  | { readonly type: 'none' }
  | { readonly type: 'all' }
  | { readonly type: 'scoped'; readonly filter: ScopeFilter };

export function parsePermissionLevel(node: unknown): PermissionLevel {
  if (typeof node === 'string') {
    switch (node) {
      case 'none': return { type: 'none' };
      case 'all': return { type: 'all' };
      default: throw new Error(`Invalid permission level: "${node}"`);
    }
  }
  if (node !== null && typeof node === 'object') {
    return { type: 'scoped', filter: ScopeFilter.fromObject(node) };
  }
  throw new Error(`Permission level must be a string or object, got: ${typeof node}`);
}
