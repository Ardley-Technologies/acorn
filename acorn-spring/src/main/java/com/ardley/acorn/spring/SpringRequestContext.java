package com.ardley.acorn.spring;

import com.ardley.acorn.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring MVC implementation of {@link RequestContext}.
 *
 * <p>Adapts an {@link HttpServletRequest} into the framework-agnostic interface
 * that Acorn extractors consume. Path variables are resolved from Spring's
 * {@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE}.
 */
public final class SpringRequestContext implements RequestContext {

    private final HttpServletRequest request;
    private final Map<String, String> pathVariables;

    @SuppressWarnings("unchecked")
    public SpringRequestContext(HttpServletRequest request) {
        this.request = request;
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        this.pathVariables = vars instanceof Map ? (Map<String, String>) vars : Map.of();
    }

    @Override
    public Optional<String> pathParam(String name) {
        return Optional.ofNullable(pathVariables.get(name));
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
}
