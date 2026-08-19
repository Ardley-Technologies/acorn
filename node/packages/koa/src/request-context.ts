import type { Context } from 'koa';
import type { RequestContext } from '@ardley-technologies/acorn-core';

export class KoaRequestContext implements RequestContext {
  constructor(private readonly ctx: Context) {}

  pathParam(name: string): string | undefined {
    return (this.ctx.params as Record<string, string> | undefined)?.[name];
  }

  queryParam(name: string): string | undefined {
    const val = this.ctx.query[name];
    if (typeof val === 'string') return val;
    if (Array.isArray(val)) return val[0];
    return undefined;
  }

  queryParams(name: string): string[] {
    const val = this.ctx.query[name];
    if (typeof val === 'string') return [val];
    if (Array.isArray(val)) return val;
    return [];
  }

  header(name: string): string | undefined {
    const val = this.ctx.headers[name.toLowerCase()];
    if (typeof val === 'string') return val;
    if (Array.isArray(val)) return val[0];
    return undefined;
  }

  path(): string {
    return this.ctx.path;
  }

  method(): string {
    return this.ctx.method;
  }
}
