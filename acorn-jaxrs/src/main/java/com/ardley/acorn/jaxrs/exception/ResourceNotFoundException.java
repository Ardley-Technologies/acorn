package com.ardley.acorn.jaxrs.exception;

/**
 * Thrown when a resource extractor cannot find the requested resource.
 *
 * <p>Applications should map this to a 404 Not Found response.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s \"%s\" not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
}
