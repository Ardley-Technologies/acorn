package com.ardley.acorn.annotation;

import com.ardley.acorn.resource.ResourceExtractor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a parameter carries a resource identifier requiring scoped authorization.
 *
 * <p>When the authorization filter encounters this annotation, it:
 * <ol>
 *   <li>Resolves the resource ID from the annotated parameter (via {@code @PathParam}
 *       or {@code @QueryParam})</li>
 *   <li>Loads the resource using the specified {@link ResourceExtractor}</li>
 *   <li>Extracts authorization attributes from the loaded resource</li>
 *   <li>Evaluates the principal's scoped permissions against those attributes</li>
 *   <li>Stores the loaded resource in the request context on success</li>
 * </ol>
 *
 * <pre>{@code
 * @PUT @Path("/{id}")
 * public Response update(
 *     @PathParam("id")
 *     @Authorized(extractor = UserExtractor.class, actions = "UpdateUser")
 *     String userId
 * ) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Authorized {
    Class<? extends ResourceExtractor<?>> extractor();
    String[] actions();
}
