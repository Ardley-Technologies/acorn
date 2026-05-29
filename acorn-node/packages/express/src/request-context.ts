import type { Request } from 'express';
import type { RequestContext } from '@ardley/acorn-core';

export class ExpressRequestContext implements RequestContext {
  constructor(private readonly req: Request) {}

  pathParam(name: string): string | undefined {
    return this.req.params?.[name];
  }

  queryParam(name: string): string | undefined {
    const val = this.req.query?.[name];
    if (typeof val === 'string') return val;
    if (Array.isArray(val) && typeof val[0] === 'string') return val[0];
    return undefined;
  }

  queryParams(name: string): string[] {
    const val = this.req.query?.[name];
    if (typeof val === 'string') return [val];
    if (Array.isArray(val)) return val.filter((v): v is string => typeof v === 'string');
    return [];
  }

  header(name: string): string | undefined {
    const val = this.req.headers[name.toLowerCase()];
    if (typeof val === 'string') return val;
    if (Array.isArray(val)) return val[0];
    return undefined;
  }

  path(): string {
    return this.req.path;
  }

  method(): string {
    return this.req.method;
  }
}
