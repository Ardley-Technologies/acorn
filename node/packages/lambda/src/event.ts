/**
 * Minimal structural types for the Lambda HTTP events this adapter understands.
 *
 * Deliberately declared here rather than imported from `@types/aws-lambda`: that
 * package is types-only, so depending on it would force a devDependency on every
 * consumer for no runtime benefit. These shapes are structurally compatible with
 * `APIGatewayProxyEvent` (payload v1), `APIGatewayProxyEventV2` (payload v2) and
 * ALB target-group events, so passing a real typed event just works.
 */

/** API Gateway payload format 2.0 (HTTP API) and Function URLs. */
export interface LambdaHttpEventV2 {
  version?: string;
  rawPath?: string;
  rawQueryString?: string;
  headers?: Record<string, string | undefined>;
  cookies?: string[];
  queryStringParameters?: Record<string, string | undefined>;
  pathParameters?: Record<string, string | undefined>;
  requestContext?: {
    http?: {
      method?: string;
      path?: string;
    };
  };
}

/** API Gateway payload format 1.0 (REST API) and ALB. */
export interface LambdaHttpEventV1 {
  httpMethod?: string;
  path?: string;
  headers?: Record<string, string | undefined>;
  multiValueHeaders?: Record<string, string[] | undefined>;
  queryStringParameters?: Record<string, string | undefined>;
  multiValueQueryStringParameters?: Record<string, string[] | undefined>;
  pathParameters?: Record<string, string | undefined>;
}

export type LambdaHttpEvent = LambdaHttpEventV1 | LambdaHttpEventV2;

/**
 * Response shape returned on denial. Compatible with
 * `APIGatewayProxyResult` and `APIGatewayProxyStructuredResultV2`.
 */
export interface LambdaHttpResponse {
  statusCode: number;
  headers?: Record<string, string>;
  body: string;
}
