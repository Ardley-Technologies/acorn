package com.ardley.acorn.policy;

import com.ardley.acorn.attribute.AttributeSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Governs how the authorization evaluator behaves beyond the permission set rules.
 *
 * <p>The primary use case is configuring isolation constraints — attributes that must
 * match between principal and resource before any permission evaluation occurs. This
 * replaces hard-coded cross-tenant checks with a declarative, user-configured policy.
 *
 * <p>Example configuration for a multi-tenant SaaS application:
 * <pre>{@code
 * EvaluationPolicy policy = EvaluationPolicy.builder()
 *     .withIsolation("tenant_id")
 *     .build();
 * }</pre>
 *
 * <p>Isolation semantics:
 * <ul>
 *   <li>If both principal and resource have the attribute with different values → deny</li>
 *   <li>If the resource has it but the principal does not → deny</li>
 *   <li>If the resource does not have the attribute → skip (resource is unscoped)</li>
 *   <li>No isolation attributes configured → no isolation enforcement</li>
 * </ul>
 */
public final class EvaluationPolicy {

    private static final EvaluationPolicy NONE = new EvaluationPolicy(List.of());

    private final List<String> isolationAttributes;

    private EvaluationPolicy(List<String> isolationAttributes) {
        this.isolationAttributes = Collections.unmodifiableList(isolationAttributes);
    }

    public static EvaluationPolicy none() {
        return NONE;
    }

    public static EvaluationPolicy withIsolation(String... attributes) {
        return new EvaluationPolicy(List.of(attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks isolation constraints between principal and resource.
     *
     * @return a denial reason if isolation is violated, or empty if the check passes
     */
    public Optional<String> checkIsolation(AttributeSource principal, AttributeSource resource) {
        for (String attr : isolationAttributes) {
            Optional<String> resourceVal = resource.attribute(attr);
            Optional<String> principalVal = principal.attribute(attr);

            if (resourceVal.isPresent() && principalVal.isPresent()
                    && !resourceVal.get().equals(principalVal.get())) {
                return Optional.of(String.format(
                        "Isolation violation on '%s': principal='%s', resource='%s'",
                        attr, principalVal.get(), resourceVal.get()));
            }

            if (resourceVal.isPresent() && principalVal.isEmpty()) {
                return Optional.of(String.format(
                        "Isolation violation: resource has '%s=%s' but principal lacks it",
                        attr, resourceVal.get()));
            }
        }
        return Optional.empty();
    }

    public List<String> isolationAttributes() {
        return isolationAttributes;
    }

    public static final class Builder {
        private final List<String> attrs = new ArrayList<>();

        public Builder withIsolation(String attributeName) {
            attrs.add(attributeName);
            return this;
        }

        public EvaluationPolicy build() {
            return new EvaluationPolicy(new ArrayList<>(attrs));
        }
    }
}
