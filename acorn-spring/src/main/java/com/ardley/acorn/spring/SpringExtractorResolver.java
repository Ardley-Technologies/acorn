package com.ardley.acorn.spring;

import com.ardley.acorn.resource.ResourceExtractor;
import org.springframework.context.ApplicationContext;

/**
 * Spring-backed extractor resolver.
 *
 * <p>Resolves {@link ResourceExtractor} instances from the Spring
 * {@link ApplicationContext}. Extractors must be registered as Spring beans
 * (via {@code @Component}, {@code @Bean}, or XML configuration).
 */
public final class SpringExtractorResolver {

    private final ApplicationContext applicationContext;

    public SpringExtractorResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Resolves a resource extractor bean by class.
     *
     * @param extractorClass the extractor class to resolve
     * @return the Spring-managed extractor instance
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if not registered
     */
    public ResourceExtractor<?> resolve(Class<? extends ResourceExtractor<?>> extractorClass) {
        return (ResourceExtractor<?>) applicationContext.getBean(extractorClass);
    }
}
