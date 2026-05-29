import type { Principal, RequestContext } from './types.js';

export interface PrincipalExtractor {
  extract(ctx: RequestContext): Promise<Principal | undefined>;
}
