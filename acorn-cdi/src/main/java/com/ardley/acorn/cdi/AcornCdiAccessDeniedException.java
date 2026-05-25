package com.ardley.acorn.cdi;

/**
 * Thrown when an authorization check fails in the CDI integration.
 *
 * <p>Applications handle this via JAX-RS exception mappers or CDI exception handlers.
 */
public class AcornCdiAccessDeniedException extends RuntimeException {

    private final String actionName;
    private final String resourceType;
    private final String resourceId;

    public AcornCdiAccessDeniedException(String message, String actionName, String resourceType, String resourceId) {
        super(message);
        this.actionName = actionName;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String actionName() { return actionName; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
}
