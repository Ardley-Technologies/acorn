import type { AttributeSource } from './types.js';
import type { AttributeFilter } from './attribute-filter.js';
import { matchesFilter, parseAttributeFilter } from './attribute-filter.js';

export class ScopeFilter {
  constructor(readonly filters: ReadonlyMap<string, AttributeFilter>) {}

  matches(principal: AttributeSource, resource: AttributeSource): boolean {
    if (this.filters.size === 0) return true;
    for (const [dimension, filter] of this.filters) {
      if (!matchesFilter(filter, dimension, principal, resource)) return false;
    }
    return true;
  }

  static fromObject(obj: unknown): ScopeFilter {
    if (obj === null || typeof obj !== 'object') {
      throw new Error('Scope filter must be an object');
    }
    const filters = new Map<string, AttributeFilter>();
    for (const [key, value] of Object.entries(obj as Record<string, unknown>)) {
      filters.set(key, parseAttributeFilter(value));
    }
    return new ScopeFilter(filters);
  }
}
