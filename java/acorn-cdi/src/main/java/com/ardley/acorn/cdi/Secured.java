package com.ardley.acorn.cdi;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI interceptor binding that activates Acorn authorization on a method.
 *
 * <p>Apply to methods alongside {@link com.ardley.acorn.annotation.RequiresActions}
 * or {@link com.ardley.acorn.annotation.Authorized} annotations to trigger
 * the {@link AuthorizationInterceptor}.
 *
 * <pre>{@code
 * @Secured
 * @RequiresActions("ListUsers")
 * public List<User> listUsers() { ... }
 * }</pre>
 *
 * <p>The interceptor must be enabled in {@code beans.xml}:
 * <pre>{@code
 * <interceptors>
 *     <class>com.ardley.acorn.cdi.AuthorizationInterceptor</class>
 * </interceptors>
 * }</pre>
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Secured {
}
