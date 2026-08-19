import type { FastifyRequest } from 'fastify';
import type { RequestContext } from '@ardley-technologies/acorn-core';

export class FastifyRequestContext implements RequestContext {
  constructor(private readonly req: FastifyRequest) {}

  pathParam(name: string): string | undefined {
    const params = this.req.params as Record<string, string> | undefined;
    return params?.[name];
  }

  queryParam(name: string): string | undefined {
    const query = this.req.query as Record<string, string | string[]> | undefined;
    const val = query?.[name];
    if (typeof val === 'string') return val;
    if (Array.isArray(val)) return val[0];
    return undefined;
  }

  queryParams(name: string): string[] {
    const query = this.req.query as Record<string, string | string[]> | undefined;
    const val = query?.[name];
    if (typeof val === 'string') return [val];
    if (Array.isArray(val)) return val;
    return [];
  }

  header(name: string): string | undefined {
    const val = this.req.headers[name.toLowerCase()];
    if (typeof val === 'string') return val;
    if (Array.isArray(val)) return val[0];
    return undefined;
  }

  path(): string {
    return this.req.url;
  }

  method(): string {
    return this.req.method;
  }
}
