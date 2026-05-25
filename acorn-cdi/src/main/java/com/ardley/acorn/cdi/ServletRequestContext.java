package com.ardley.acorn.cdi;

import com.ardley.acorn.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Servlet-based implementation of {@link RequestContext} for CDI environments.
 *
 * <p>Adapts a raw {@link HttpServletRequest}. Path parameters must be set as
 * request attributes by the servlet container or a routing framework (JAX-RS,
 * RESTEasy, etc.) before this context is used.
 *
 * <p>The path parameter attribute convention follows JAX-RS:
 * attribute name {@code "jakarta.ws.rs.pathparam.<name>"} or a custom prefix
 * configurable at construction.
 */
public final class ServletRequestContext implements RequestContext {

    private static final String PATH_PARAM_PREFIX = "acorn.pathparam.";

    private final HttpServletRequest request;

    public ServletRequestContext(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Optional<String> pathParam(String name) {
        return Optional.ofNullable((String) request.getAttribute(PATH_PARAM_PREFIX + name));
    }

    @Override
    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(request.getParameter(name));
    }

    @Override
    public List<String> queryParams(String name) {
        String[] values = request.getParameterValues(name);
        return values != null ? Arrays.asList(values) : List.of();
    }

    @Override
    public Optional<String> header(String name) {
        return Optional.ofNullable(request.getHeader(name));
    }

    @Override
    public String path() {
        return request.getRequestURI();
    }

    @Override
    public String method() {
        return request.getMethod();
    }

    @Override
    public Optional<Object> property(String name) {
        return Optional.ofNullable(request.getAttribute(name));
    }

    /**
     * Sets a path parameter value. Call this from your routing integration
     * before authorization runs.
     */
    public static void setPathParam(HttpServletRequest request, String name, String value) {
        request.setAttribute(PATH_PARAM_PREFIX + name, value);
    }
}
