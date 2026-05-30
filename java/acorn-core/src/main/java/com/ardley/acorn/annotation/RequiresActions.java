package com.ardley.acorn.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a resource method requires the principal to hold specific actions.
 *
 * <p>Performs a gate check — verifies the principal's permission set contains the
 * declared actions without evaluating against a specific resource. All declared
 * actions must pass (AND semantics).
 *
 * <pre>{@code
 * @GET
 * @RequiresActions("ListUsers")
 * public Response listUsers() { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresActions {
    String[] value();
}
