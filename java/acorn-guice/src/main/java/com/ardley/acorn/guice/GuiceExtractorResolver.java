package com.ardley.acorn.guice;

import com.ardley.acorn.jaxrs.filter.ExtractorResolver;
import com.ardley.acorn.resource.ResourceExtractor;
import com.google.inject.Injector;

/**
 * Guice-backed implementation of {@link ExtractorResolver}.
 *
 * <p>Resolves resource extractor instances via the Guice {@link Injector}. Extractors
 * are resolved by their class — they must be bound in a Guice module (either explicitly
 * or via JIT binding).
 */
public final class GuiceExtractorResolver implements ExtractorResolver {

    private final Injector injector;

    public GuiceExtractorResolver(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ResourceExtractor<?> resolve(Class<? extends ResourceExtractor<?>> extractorClass) {
        return (ResourceExtractor<?>) injector.getInstance(extractorClass);
    }
}
