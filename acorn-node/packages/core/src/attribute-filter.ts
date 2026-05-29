import type { AttributeSource } from './types.js';

export type AttributeFilter =
  | { readonly type: 'matchPrincipal' }
  | { readonly type: 'matchPrincipalAttribute'; readonly principalAttribute: string }
  | { readonly type: 'matchPrincipalWithFallbacks'; readonly principalAttributes: readonly string[] }
  | { readonly type: 'equals'; readonly value: string }
  | { readonly type: 'inList'; readonly values: readonly string[] };

export function matchesFilter(
  filter: AttributeFilter,
  dimension: string,
  principal: AttributeSource,
  resource: AttributeSource,
): boolean {
  const resourceVal = resource.attribute(dimension);
  if (resourceVal === undefined) return false;

  switch (filter.type) {
    case 'matchPrincipal': {
      const principalVal = principal.attribute(dimension);
      return principalVal !== undefined && resourceVal === principalVal;
    }
    case 'matchPrincipalAttribute': {
      const principalVal = principal.attribute(filter.principalAttribute);
      return principalVal !== undefined && resourceVal === principalVal;
    }
    case 'matchPrincipalWithFallbacks': {
      for (const attr of filter.principalAttributes) {
        const principalVal = principal.attribute(attr);
        if (principalVal !== undefined && resourceVal === principalVal) return true;
      }
      return false;
    }
    case 'equals':
      return resourceVal === filter.value;
    case 'inList':
      return filter.values.includes(resourceVal);
  }
}

export function parseAttributeFilter(node: unknown): AttributeFilter {
  if (node === null || typeof node !== 'object') {
    throw new Error('Attribute filter must be an object');
  }

  const obj = node as Record<string, unknown>;

  if ('match' in obj && obj.match === 'principal') {
    return { type: 'matchPrincipal' };
  }
  if ('matchPrincipalAttribute' in obj && typeof obj.matchPrincipalAttribute === 'string') {
    return { type: 'matchPrincipalAttribute', principalAttribute: obj.matchPrincipalAttribute };
  }
  if ('matchPrincipalAttributes' in obj && Array.isArray(obj.matchPrincipalAttributes)) {
    return { type: 'matchPrincipalWithFallbacks', principalAttributes: [...obj.matchPrincipalAttributes] };
  }
  if ('equals' in obj && typeof obj.equals === 'string') {
    return { type: 'equals', value: obj.equals };
  }
  if ('in' in obj && Array.isArray(obj.in)) {
    return { type: 'inList', values: [...obj.in] };
  }

  throw new Error(`Unrecognized attribute filter: ${JSON.stringify(node)}`);
}
