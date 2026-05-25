package com.ardley.acorn.spring;

/**
 * Thrown when an authorization check fails in the Spring integration.
 *
 * <p>Carries structured context about what was denied so {@code @ControllerAdvice}
 * exception handlers can produce informative error responses.
 *
 * <p>Applications handle this via:
 * <pre>{@code
 * @ControllerAdvice
 * public class AuthExceptionHandler {
 *     @ExceptionHandler(AcornAccessDeniedException.class)
 *     public ResponseEntity<?> handle(AcornAccessDeniedException e) {
 *         return ResponseEntity.status(403).body(problemDetails(e));
 *     }
 * }
 * }</pre>
 */
public class AcornAccessDeniedException extends RuntimeException {

    private final String actionName;
    private final String resourceType;
    private final String resourceId;

    public AcornAccessDeniedException(String message, String actionName, String resourceType, String resourceId) {
        super(message);
        this.actionName = actionName;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String actionName() { return actionName; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
}
