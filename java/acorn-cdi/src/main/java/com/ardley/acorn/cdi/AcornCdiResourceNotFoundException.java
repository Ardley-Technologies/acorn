package com.ardley.acorn.cdi;

/**
 * Thrown when a resource extractor cannot find the requested resource in the CDI integration.
 */
public class AcornCdiResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public AcornCdiResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s \"%s\" not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
}
