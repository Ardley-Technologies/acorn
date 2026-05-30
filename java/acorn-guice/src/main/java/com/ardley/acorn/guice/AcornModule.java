package com.ardley.acorn.guice;

import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.jaxrs.filter.AuthorizationFilter;
import com.ardley.acorn.jaxrs.filter.ExtractorResolver;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.resource.ResourceExtractor;
import com.ardley.acorn.store.PermissionStore;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Base Guice module for Acorn authorization integration.
 *
 * <p>Extend this module to provide your application's bindings for
 * {@link PrincipalExtractor}, {@link PermissionStore}, {@link EvaluationPolicy},
 * and resource extractors.
 *
 * <pre>{@code
 * public class MyAuthModule extends AcornModule {
 *
 *     @Override
 *     protected void configureAcorn() {
 *         bindPolicy(EvaluationPolicy.withIsolation("tenant_id"));
 *         bindPrincipalExtractor(JwtPrincipalExtractor.class);
 *         bindExtractor(UserExtractor.class);
 *         bindExtractor(DocumentExtractor.class);
 *     }
 *
 *     @Provides @Singleton
 *     PermissionStore provideStore(PersistenceClient client) {
 *         return new CachingPermissionStore(
 *             new PersistencePermissionLoader(client),
 *             Duration.ofMinutes(5), 10_000);
 *     }
 *
 *     @Provides @Singleton
 *     ActionRegistry provideActions() {
 *         ActionRegistry registry = new ActionRegistry();
 *         registry.registerAll(UserActions.class);
 *         registry.registerAll(DocumentActions.class);
 *         return registry;
 *     }
 * }
 * }</pre>
 */
public abstract class AcornModule extends AbstractModule {

    @Override
    protected final void configure() {
        bind(AuthorizationFilter.class).in(Singleton.class);
        bind(ExtractorResolver.class).to(GuiceExtractorResolver.class).in(Singleton.class);

        configureAcorn();
    }

    /**
     * Override to provide your application-specific bindings.
     */
    protected abstract void configureAcorn();

    /**
     * Binds the evaluation policy.
     */
    protected void bindPolicy(EvaluationPolicy policy) {
        bind(EvaluationPolicy.class).toInstance(policy);
    }

    /**
     * Binds the principal extractor implementation.
     */
    protected <T extends PrincipalExtractor> void bindPrincipalExtractor(Class<T> extractorClass) {
        bind(PrincipalExtractor.class).to(extractorClass).in(Singleton.class);
    }

    /**
     * Registers a resource extractor by its class. Available for
     * {@link com.ardley.acorn.annotation.Authorized} annotations.
     */
    protected <T extends ResourceExtractor<?>> void bindExtractor(Class<T> extractorClass) {
        bind(extractorClass).in(Singleton.class);
    }

    @Provides
    @Singleton
    GuiceExtractorResolver provideExtractorResolver(Injector injector) {
        return new GuiceExtractorResolver(injector);
    }
}
