package com.ardley.acorn.cdi;

/**
 * Thrown when no authenticated principal is available in the CDI integration.
 */
public class AcornCdiAuthenticationException extends RuntimeException {

    public AcornCdiAuthenticationException(String message) {
        super(message);
    }
}
