package com.ardley.acorn.jaxrs.filter;

import com.ardley.acorn.resource.ResourceExtractor;

/**
 * Resolves {@link ResourceExtractor} instances by class at request time.
 *
 * <p>This is the bridge between the JAX-RS filter and your dependency injection
 * framework. Implementations delegate to their DI container's instance resolution.
 *
 * <p>Provided implementations:
 * <ul>
 *   <li>{@code acorn-guice}: resolves via Guice Injector</li>
 *   <li>{@code acorn-spring} (future): resolves via ApplicationContext</li>
 * </ul>
 */
public interface ExtractorResolver {

    /**
     * Returns an instance of the specified extractor class.
     *
     * @param extractorClass the extractor class declared in {@link com.ardley.acorn.annotation.Authorized#extractor()}
     * @return the resolved instance
     * @throws IllegalArgumentException if the extractor cannot be resolved
     */
    ResourceExtractor<?> resolve(Class<? extends ResourceExtractor<?>> extractorClass);
}
