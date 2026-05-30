package com.ardley.acorn.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables Acorn authorization in a Spring application.
 *
 * <p>Apply to a {@code @Configuration} class to auto-register the
 * {@link AcornInterceptor} and {@link SpringExtractorResolver}.
 *
 * <p>You must still provide beans for:
 * <ul>
 *   <li>{@link com.ardley.acorn.attribute.PrincipalExtractor}</li>
 *   <li>{@link com.ardley.acorn.store.PermissionStore}</li>
 *   <li>{@link com.ardley.acorn.policy.EvaluationPolicy}</li>
 *   <li>{@link com.ardley.acorn.action.ActionRegistry}</li>
 *   <li>Your {@link com.ardley.acorn.resource.ResourceExtractor} implementations</li>
 * </ul>
 *
 * <pre>{@code
 * @Configuration
 * @EnableAcorn
 * public class SecurityConfig {
 *     @Bean EvaluationPolicy policy() { return EvaluationPolicy.withIsolation("tenant_id"); }
 *     @Bean PrincipalExtractor principal() { return new JwtPrincipalExtractor(); }
 *     // ...
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(AcornSpringConfiguration.class)
public @interface EnableAcorn {
}
