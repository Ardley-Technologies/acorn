package com.ardley.acorn.attribute;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable attribute bag for constructing resource or principal attribute sets.
 *
 * <p>Use the builder to assemble attributes from domain objects:
 * <pre>{@code
 * Attributes attrs = Attributes.builder()
 *     .with("tenant_id", user.getTenantId())
 *     .with("department", user.getDepartment())
 *     .with("status", user.getStatus())
 *     .build();
 * }</pre>
 *
 * <p>Null values are silently ignored — only non-null attributes are stored.
 */
public final class Attributes implements AttributeSource {

    private static final Attributes EMPTY = new Attributes(Map.of());

    private final Map<String, String> entries;

    private Attributes(Map<String, String> entries) {
        this.entries = entries;
    }

    public static Attributes empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<String> attribute(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    /**
     * Returns an unmodifiable view of all stored attributes.
     */
    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(entries);
    }

    public static final class Builder {
        private final Map<String, String> entries = new HashMap<>();

        /**
         * Adds an attribute. Null values are ignored.
         */
        public Builder with(String key, String value) {
            if (value != null) {
                entries.put(key, value);
            }
            return this;
        }

        public Attributes build() {
            return new Attributes(Map.copyOf(entries));
        }
    }
}
