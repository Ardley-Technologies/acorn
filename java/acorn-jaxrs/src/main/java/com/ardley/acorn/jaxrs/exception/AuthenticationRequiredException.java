package com.ardley.acorn.jaxrs.exception;

/**
 * Thrown when no authenticated principal is available in the request context.
 *
 * <p>This indicates the request reached the authorization filter without first
 * passing through an authentication filter that sets the principal. Applications
 * should map this to a 401 Unauthorized response.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("No authenticated principal found in request context");
    }
}
