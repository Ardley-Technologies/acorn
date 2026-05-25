package com.ardley.acorn.permission;

import com.ardley.acorn.attribute.AttributeSource;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Defines how a single attribute dimension is evaluated during scope filtering.
 *
 * <p>Each filter variant compares a resource attribute (keyed by the dimension name
 * in the containing {@link ScopeFilter}) against principal attributes or literal values.
 *
 * <p>Implementations are parsed from JSON permission configurations and evaluated
 * at request time by the {@link com.ardley.acorn.evaluator.Evaluator}.
 */
public sealed interface AttributeFilter {

    /**
     * Evaluates this filter for the given dimension.
     *
     * @param dimension the attribute name on the resource being tested
     * @param principal the requesting principal's attributes
     * @param resource the target resource's attributes
     * @return true if the filter condition is satisfied
     */
    boolean matches(String dimension, AttributeSource principal, AttributeSource resource);

    /**
     * The resource attribute must equal the same-named attribute on the principal.
     * <p>JSON representation: {@code {"match": "principal"}}
     */
    record MatchPrincipal() implements AttributeFilter {
        @Override
        public boolean matches(String dimension, AttributeSource principal, AttributeSource resource) {
            Optional<String> r = resource.attribute(dimension);
            Optional<String> p = principal.attribute(dimension);
            return r.isPresent() && p.isPresent() && r.get().equals(p.get());
        }
    }

    /**
     * The resource attribute must equal a differently-named attribute on the principal.
     * <p>JSON representation: {@code {"matchPrincipalAttribute": "userId"}}
     */
    record MatchPrincipalAttribute(String principalAttribute) implements AttributeFilter {
        @Override
        public boolean matches(String dimension, AttributeSource principal, AttributeSource resource) {
            Optional<String> r = resource.attribute(dimension);
            Optional<String> p = principal.attribute(principalAttribute);
            return r.isPresent() && p.isPresent() && r.get().equals(p.get());
        }
    }

    /**
     * The resource attribute must match one of several principal attributes.
     * The first match in the ordered list wins.
     * <p>JSON representation: {@code {"matchPrincipalAttributes": ["userId", "email"]}}
     */
    record MatchPrincipalWithFallbacks(List<String> principalAttributes) implements AttributeFilter {
        @Override
        public boolean matches(String dimension, AttributeSource principal, AttributeSource resource) {
            Optional<String> resourceVal = resource.attribute(dimension);
            if (resourceVal.isEmpty()) return false;

            for (String attr : principalAttributes) {
                Optional<String> principalVal = principal.attribute(attr);
                if (principalVal.isPresent() && resourceVal.get().equals(principalVal.get())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The resource attribute must equal a specific literal value.
     * <p>JSON representation: {@code {"equals": "active"}}
     */
    record Equals(String value) implements AttributeFilter {
        @Override
        public boolean matches(String dimension, AttributeSource principal, AttributeSource resource) {
            return resource.attribute(dimension)
                    .map(v -> v.equals(value))
                    .orElse(false);
        }
    }

    /**
     * The resource attribute must be contained in a set of allowed values.
     * <p>JSON representation: {@code {"in": ["Sales", "Marketing"]}}
     */
    record InList(List<String> values) implements AttributeFilter {
        @Override
        public boolean matches(String dimension, AttributeSource principal, AttributeSource resource) {
            return resource.attribute(dimension)
                    .map(values::contains)
                    .orElse(false);
        }
    }

    /**
     * Parses an {@link AttributeFilter} from its JSON representation.
     *
     * @param node the JSON node representing the filter
     * @return the parsed filter
     * @throws IllegalArgumentException if the JSON structure is not recognized
     */
    static AttributeFilter fromJson(JsonNode node) {
        if (node.has("match") && "principal".equals(node.get("match").asText())) {
            return new MatchPrincipal();
        }
        if (node.has("matchPrincipalAttribute")) {
            return new MatchPrincipalAttribute(node.get("matchPrincipalAttribute").asText());
        }
        if (node.has("matchPrincipalAttributes")) {
            List<String> attrs = new ArrayList<>();
            node.get("matchPrincipalAttributes").forEach(n -> attrs.add(n.asText()));
            return new MatchPrincipalWithFallbacks(List.copyOf(attrs));
        }
        if (node.has("equals")) {
            return new Equals(node.get("equals").asText());
        }
        if (node.has("in")) {
            List<String> values = new ArrayList<>();
            node.get("in").forEach(n -> values.add(n.asText()));
            return new InList(List.copyOf(values));
        }
        throw new IllegalArgumentException("Unrecognized attribute filter: " + node);
    }
}
