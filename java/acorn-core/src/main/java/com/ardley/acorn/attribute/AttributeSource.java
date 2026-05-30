package com.ardley.acorn.attribute;

import java.util.Optional;

/**
 * A source of named string attributes for authorization evaluation.
 *
 * <p>Both principals and resources implement this interface. The authorization
 * framework never interprets what attributes mean — it only compares their values
 * according to scope filter rules defined in the permission configuration.
 *
 * <p>This is the foundational abstraction of Acorn: authorization decisions are
 * made by comparing attribute values between two sources (principal and resource)
 * using declarative rules. No hard-coded field access, no framework-specific types.
 */
public interface AttributeSource {

    /**
     * Returns the value of the named attribute, or empty if the attribute is not present.
     *
     * @param name the attribute name (e.g., "tenant_id", "department", "status")
     * @return the attribute value, or empty
     */
    Optional<String> attribute(String name);
}
