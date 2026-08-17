import { describe, test, expect } from 'bun:test';
import { LambdaRequestContext } from '../request-context.js';
import type { LambdaHttpEventV1, LambdaHttpEventV2 } from '../event.js';

const v2 = (over: Partial<LambdaHttpEventV2> = {}): LambdaHttpEventV2 => ({
  version: '2.0',
  rawPath: '/users/42',
  headers: { 'content-type': 'application/json', authorization: 'Bearer t' },
  queryStringParameters: { region: 'US' },
  pathParameters: { id: '42' },
  requestContext: { http: { method: 'GET', path: '/users/42' } },
  ...over,
});

const v1 = (over: Partial<LambdaHttpEventV1> = {}): LambdaHttpEventV1 => ({
  httpMethod: 'POST',
  path: '/users/42',
  headers: { 'Content-Type': 'application/json' },
  queryStringParameters: { region: 'US' },
  pathParameters: { id: '42' },
  ...over,
});

describe('LambdaRequestContext — payload v2', () => {
  test('reads method and path from requestContext.http', () => {
    const ctx = new LambdaRequestContext(v2());
    expect(ctx.method()).toBe('GET');
    expect(ctx.path()).toBe('/users/42');
  });

  test('falls back to rawPath when requestContext.http.path is absent', () => {
    const ctx = new LambdaRequestContext(v2({ requestContext: { http: { method: 'GET' } } }));
    expect(ctx.path()).toBe('/users/42');
  });

  test('reads path and query parameters', () => {
    const ctx = new LambdaRequestContext(v2());
    expect(ctx.pathParam('id')).toBe('42');
    expect(ctx.pathParam('nope')).toBeUndefined();
    expect(ctx.queryParam('region')).toBe('US');
    expect(ctx.queryParam('nope')).toBeUndefined();
  });

  test('splits comma-joined repeated query parameters', () => {
    // API Gateway v2 joins repeats with commas and provides no multi-value map.
    const ctx = new LambdaRequestContext(v2({ queryStringParameters: { tag: 'a,b,c' } }));
    expect(ctx.queryParams('tag')).toEqual(['a', 'b', 'c']);
  });

  test('queryParams returns [] for an absent parameter', () => {
    expect(new LambdaRequestContext(v2()).queryParams('nope')).toEqual([]);
  });

  test('header lookup is case-insensitive', () => {
    const ctx = new LambdaRequestContext(v2());
    expect(ctx.header('Authorization')).toBe('Bearer t');
    expect(ctx.header('AUTHORIZATION')).toBe('Bearer t');
    expect(ctx.header('authorization')).toBe('Bearer t');
    expect(ctx.header('x-missing')).toBeUndefined();
  });
});

describe('LambdaRequestContext — payload v1', () => {
  test('reads method and path from the top level', () => {
    const ctx = new LambdaRequestContext(v1());
    expect(ctx.method()).toBe('POST');
    expect(ctx.path()).toBe('/users/42');
  });

  test('prefers the exact multi-value map over comma splitting', () => {
    const ctx = new LambdaRequestContext(
      v1({
        // A value that itself contains a comma — recoverable on v1, not on v2.
        multiValueQueryStringParameters: { tag: ['a,b', 'c'] },
        queryStringParameters: { tag: 'a,b,c' },
      }),
    );
    expect(ctx.queryParams('tag')).toEqual(['a,b', 'c']);
  });

  test('queryParam falls back to the multi-value map', () => {
    const ctx = new LambdaRequestContext(
      v1({ queryStringParameters: undefined, multiValueQueryStringParameters: { tag: ['x', 'y'] } }),
    );
    expect(ctx.queryParam('tag')).toBe('x');
  });

  test('header lookup handles mixed-case keys and multiValueHeaders', () => {
    const ctx = new LambdaRequestContext(
      v1({ headers: {}, multiValueHeaders: { 'X-Trace-Id': ['abc'] } }),
    );
    expect(ctx.header('x-trace-id')).toBe('abc');
  });

  test('mixed-case header key in the single-value map', () => {
    expect(new LambdaRequestContext(v1()).header('content-type')).toBe('application/json');
  });
});

describe('LambdaRequestContext — degenerate events', () => {
  test('returns empty strings rather than throwing on an empty event', () => {
    const ctx = new LambdaRequestContext({});
    expect(ctx.path()).toBe('');
    expect(ctx.method()).toBe('');
    expect(ctx.pathParam('id')).toBeUndefined();
    expect(ctx.queryParams('tag')).toEqual([]);
    expect(ctx.header('authorization')).toBeUndefined();
  });
});
