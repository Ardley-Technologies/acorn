package com.ardley.acorn.spring;

import com.ardley.acorn.action.ActionRegistry;
import com.ardley.acorn.attribute.PrincipalExtractor;
import com.ardley.acorn.policy.EvaluationPolicy;
import com.ardley.acorn.store.PermissionStore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration imported by {@link EnableAcorn}.
 *
 * <p>Registers the {@link AcornInterceptor} with Spring MVC and wires all
 * required dependencies from the application context.
 */
@Configuration
public class AcornSpringConfiguration implements WebMvcConfigurer {

    private final AcornInterceptor interceptor;

    public AcornSpringConfiguration(
            PrincipalExtractor principalExtractor,
            PermissionStore permissionStore,
            EvaluationPolicy policy,
            ActionRegistry actionRegistry,
            ApplicationContext applicationContext) {
        SpringExtractorResolver resolver = new SpringExtractorResolver(applicationContext);
        this.interceptor = new AcornInterceptor(
                principalExtractor, permissionStore, policy, resolver, actionRegistry);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

    @Bean
    public SpringExtractorResolver acornExtractorResolver(ApplicationContext ctx) {
        return new SpringExtractorResolver(ctx);
    }
}
