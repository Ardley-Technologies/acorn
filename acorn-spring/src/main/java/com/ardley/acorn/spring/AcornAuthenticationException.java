package com.ardley.acorn.spring;

/**
 * Thrown when no authenticated principal is available.
 *
 * <p>Applications should map this to a 401 Unauthorized response via
 * {@code @ControllerAdvice} or Spring Security's authentication entry point.
 */
public class AcornAuthenticationException extends RuntimeException {

    public AcornAuthenticationException(String message) {
        super(message);
    }
}
