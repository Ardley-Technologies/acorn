package com.ardley.acorn.jaxrs.filter;

import com.ardley.acorn.context.RequestContext;
import jakarta.ws.rs.container.ContainerRequestContext;

import java.util.List;
import java.util.Optional;

/**
 * JAX-RS implementation of {@link RequestContext}.
 *
 * <p>Adapts a {@link ContainerRequestContext} into the framework-agnostic interface
 * that Acorn extractors consume. Constructed by the authorization filter at the
 * start of each request.
 */
public final class JaxRsRequestContext implements RequestContext {

    private final ContainerRequestContext ctx;

    public JaxRsRequestContext(ContainerRequestContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<String> pathParam(String name) {
        return Optional.ofNullable(ctx.getUriInfo().getPathParameters().getFirst(name));
    }

    @Override
    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(ctx.getUriInfo().getQueryParameters().getFirst(name));
    }

    @Override
    public List<String> queryParams(String name) {
        List<String> values = ctx.getUriInfo().getQueryParameters().get(name);
        return values != null ? values : List.of();
    }

    @Override
    public Optional<String> header(String name) {
        return Optional.ofNullable(ctx.getHeaderString(name));
    }

    @Override
    public String path() {
        return ctx.getUriInfo().getPath();
    }

    @Override
    public String method() {
        return ctx.getMethod();
    }

    @Override
    public Optional<Object> property(String name) {
        return Optional.ofNullable(ctx.getProperty(name));
    }
}
