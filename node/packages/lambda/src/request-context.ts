import type { RequestContext } from '@ardley-technologies/acorn-core';
import type { LambdaHttpEvent, LambdaHttpEventV1, LambdaHttpEventV2 } from './event.js';

function isV2(event: LambdaHttpEvent): event is LambdaHttpEventV2 {
  return 'requestContext' in event || 'rawPath' in event || 'cookies' in event;
}

/**
 * `RequestContext` over an API Gateway / Function URL / ALB Lambda event.
 *
 * Handles both payload formats. The differences that matter here:
 *
 * - **Method and path** live in `requestContext.http` on v2 and at the top level
 *   on v1.
 * - **Repeated query parameters** are exact on v1
 *   (`multiValueQueryStringParameters`) but *comma-joined* on v2, which does not
 *   provide a multi-value map at all. See `queryParams()`.
 * - **Headers** are lowercased by API Gateway on v2 but not guaranteed on v1, so
 *   lookups here are case-insensitive either way.
 */
export class LambdaRequestContext implements RequestContext {
  constructor(private readonly event: LambdaHttpEvent) {}

  pathParam(name: string): string | undefined {
    return this.event.pathParameters?.[name] ?? undefined;
  }

  queryParam(name: string): string | undefined {
    const val = this.event.queryStringParameters?.[name];
    if (typeof val === 'string') return val;

    // v1 may carry only the multi-value map when a parameter repeats.
    const multi = (this.event as LambdaHttpEventV1).multiValueQueryStringParameters?.[name];
    return multi?.[0];
  }

  /**
   * All values for a repeated query parameter.
   *
   * On payload v1 this is exact. On payload v2 API Gateway joins repeated
   * parameters with commas before invoking the function and provides no
   * multi-value map, so the original values cannot be recovered when a value
   * itself contains a comma — `?tag=a,b` and `?tag=a&tag=b` arrive identically.
   * Splitting is the best available behaviour, but do not rely on it for values
   * that may contain commas; use a scope filter on a single value, or v1, if that
   * distinction matters.
   */
  queryParams(name: string): string[] {
    const multi = (this.event as LambdaHttpEventV1).multiValueQueryStringParameters?.[name];
    if (multi !== undefined) return multi;

    const val = this.event.queryStringParameters?.[name];
    if (typeof val !== 'string') return [];
    if (val === '') return [''];
    return val.split(',');
  }

  header(name: string): string | undefined {
    const target = name.toLowerCase();

    const headers = this.event.headers;
    if (headers) {
      for (const key of Object.keys(headers)) {
        if (key.toLowerCase() === target) {
          const val = headers[key];
          if (typeof val === 'string') return val;
        }
      }
    }

    const multi = (this.event as LambdaHttpEventV1).multiValueHeaders;
    if (multi) {
      for (const key of Object.keys(multi)) {
        if (key.toLowerCase() === target) return multi[key]?.[0];
      }
    }

    return undefined;
  }

  path(): string {
    if (isV2(this.event)) {
      return this.event.requestContext?.http?.path ?? this.event.rawPath ?? '';
    }
    return this.event.path ?? '';
  }

  method(): string {
    if (isV2(this.event)) {
      return this.event.requestContext?.http?.method ?? '';
    }
    return this.event.httpMethod ?? '';
  }
}
