package com.ardley.acorn.cdi;

import com.ardley.acorn.resource.ResourceExtractor;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

/**
 * CDI-backed extractor resolver.
 *
 * <p>Resolves {@link ResourceExtractor} instances from the CDI container.
 * Extractors must be CDI beans (annotated with {@code @ApplicationScoped},
 * {@code @Dependent}, or discovered via bean archives).
 */
public final class CdiExtractorResolver {

    private final BeanManager beanManager;

    public CdiExtractorResolver(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    /**
     * Creates a resolver using the current CDI container.
     */
    public static CdiExtractorResolver fromCurrentContainer() {
        return new CdiExtractorResolver(CDI.current().getBeanManager());
    }

    /**
     * Resolves a resource extractor bean by class.
     *
     * @param extractorClass the extractor class to resolve
     * @return the CDI-managed extractor instance
     * @throws IllegalArgumentException if the bean cannot be found
     */
    @SuppressWarnings("unchecked")
    public ResourceExtractor<?> resolve(Class<? extends ResourceExtractor<?>> extractorClass) {
        var beans = beanManager.getBeans(extractorClass);
        if (beans.isEmpty()) {
            throw new IllegalArgumentException(
                    "No CDI bean found for extractor: " + extractorClass.getName());
        }
        var bean = beanManager.resolve(beans);
        var context = beanManager.createCreationalContext(bean);
        return (ResourceExtractor<?>) beanManager.getReference(bean, extractorClass, context);
    }
}
