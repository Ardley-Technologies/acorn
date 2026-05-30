package com.ardley.acorn.attribute;

import com.ardley.acorn.context.RequestContext;

import java.util.Optional;

/**
 * Resolves the authenticated principal from an incoming request.
 *
 * <p>Implementations are responsible for extracting identity and role information
 * from the request context — whether from JWT tokens in headers, session cookies,
 * API keys, or any other authentication mechanism.
 *
 * <p>The Acorn filter invokes this extractor before any authorization checks.
 * If extraction fails (returns empty), the filter treats the request as unauthenticated.
 *
 * <p>Example:
 * <pre>{@code
 * public class JwtPrincipalExtractor implements PrincipalExtractor {
 *
 *     public Optional<Principal> extract(RequestContext context) {
 *         return context.header("Authorization")
 *             .filter(h -> h.startsWith("Bearer "))
 *             .map(h -> h.substring(7))
 *             .map(this::decodeAndValidate);
 *     }
 * }
 * }</pre>
 */
public interface PrincipalExtractor {

    /**
     * Extracts the authenticated principal from the request context.
     *
     * @param context the framework-agnostic request context
     * @return the resolved principal, or empty if the request is unauthenticated
     */
    Optional<Principal> extract(RequestContext context);
}
