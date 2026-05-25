package com.ardley.acorn.context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A framework-agnostic representation of an incoming HTTP request's extractable context.
 *
 * <p>Provides access to path parameters, query parameters, and headers without coupling
 * extractors to any specific HTTP framework (JAX-RS, Spring MVC, Servlet API, etc.).
 *
 * <p>Framework integration modules are responsible for constructing this from their
 * native request types (e.g., {@code ContainerRequestContext} for JAX-RS,
 * {@code HttpServletRequest} for Spring).
 */
public interface RequestContext {

    /**
     * Returns the value of a named path parameter, or empty if not present.
     *
     * @param name the parameter name as declared in the route template (e.g., "id" from "/{id}")
     */
    Optional<String> pathParam(String name);

    /**
     * Returns the first value of a named query parameter, or empty if not present.
     *
     * @param name the query parameter name
     */
    Optional<String> queryParam(String name);

    /**
     * Returns all values of a named query parameter (for multi-valued params).
     *
     * @param name the query parameter name
     * @return list of values, empty list if not present
     */
    List<String> queryParams(String name);

    /**
     * Returns the first value of a named request header, or empty if not present.
     *
     * @param name the header name (case-insensitive)
     */
    Optional<String> header(String name);

    /**
     * Returns the full request path (e.g., "/users/u-123/documents").
     */
    String path();

    /**
     * Returns the HTTP method (GET, POST, PUT, DELETE, etc.).
     */
    String method();

    /**
     * Returns a named request property/attribute set by upstream middleware.
     * Used for passing data between filters.
     *
     * @param name the property name
     * @return the property value, or empty
     */
    Optional<Object> property(String name);
}
