import type { AttributeSource, RequestContext } from './types.js';
import type { Attributes } from './attributes.js';

export interface ResourceExtractor<R = unknown> {
  resourceType(): string;
  extractId(ctx: RequestContext): string | undefined;
  load(resourceId: string, principal: AttributeSource): Promise<R | null>;
  attributes(resource: R): Attributes;
}
