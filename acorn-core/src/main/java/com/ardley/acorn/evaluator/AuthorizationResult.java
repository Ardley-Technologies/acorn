package com.ardley.acorn.evaluator;

import java.util.Optional;

/**
 * The outcome of an authorization evaluation.
 *
 * <p>Represents either a successful authorization (the principal is permitted to
 * perform the action) or a denial with a human-readable reason explaining why
 * access was refused.
 *
 * <p>This is a value object — it carries no side effects and can be safely logged,
 * serialized, cached, or passed between layers.
 */
public record AuthorizationResult(boolean permitted, String reason) {

    private static final AuthorizationResult PERMITTED = new AuthorizationResult(true, null);

    public static AuthorizationResult allowed() {
        return PERMITTED;
    }

    public static AuthorizationResult denied(String reason) {
        return new AuthorizationResult(false, reason);
    }

    public boolean isDenied() {
        return !permitted;
    }

    public Optional<String> denialReason() {
        return Optional.ofNullable(reason);
    }
}
