package com.ardley.acorn.jaxrs.exception;

/**
 * Thrown when an authorization check fails.
 *
 * <p>The framework throws this exception from the {@link AuthorizationFilter} rather than
 * constructing a response directly. This allows applications to register their own
 * {@link jakarta.ws.rs.ext.ExceptionMapper} to control the response format (RFC 9457
 * Problem Details, custom JSON, XML, etc.).
 *
 * <p>Carries structured context about what was denied so exception mappers can produce
 * informative error responses.
 */
public class AuthorizationDeniedException extends RuntimeException {

    private final String actionName;
    private final String resourceType;
    private final String resourceId;
    private final DenialKind kind;

    private AuthorizationDeniedException(String message, String actionName, String resourceType, String resourceId, DenialKind kind) {
        super(message);
        this.actionName = actionName;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.kind = kind;
    }

    /**
     * Gate check denial — no specific resource involved.
     */
    public static AuthorizationDeniedException gateCheck(String actionName, String reason) {
        return new AuthorizationDeniedException(reason, actionName, null, null, DenialKind.GATE);
    }

    /**
     * Resource-level denial — a specific resource was evaluated.
     */
    public static AuthorizationDeniedException resourceCheck(String actionName, String resourceType, String resourceId, String reason) {
        return new AuthorizationDeniedException(reason, actionName, resourceType, resourceId, DenialKind.RESOURCE);
    }

    /**
     * No permission set found for the principal's role.
     */
    public static AuthorizationDeniedException noPermissions(String reason) {
        return new AuthorizationDeniedException(reason, null, null, null, DenialKind.NO_PERMISSIONS);
    }

    public String actionName() { return actionName; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public DenialKind kind() { return kind; }

    /**
     * Classifies the type of denial for mapper use.
     */
    public enum DenialKind {
        /** Gate check failed — action not permitted for this role at all. */
        GATE,
        /** Resource check failed — action not permitted on this specific resource. */
        RESOURCE,
        /** No permission set could be loaded for the principal. */
        NO_PERMISSIONS
    }
}
