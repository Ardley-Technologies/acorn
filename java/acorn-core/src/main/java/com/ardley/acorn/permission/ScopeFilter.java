package com.ardley.acorn.permission;

import com.ardley.acorn.attribute.AttributeSource;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * A multi-dimensional scope filter that restricts an action to resources matching
 * specific attribute conditions.
 *
 * <p>All dimensions are evaluated with AND semantics — every filter in the map must
 * match for the scope to be satisfied. An empty filter matches everything.
 *
 * <p>JSON representation:
 * <pre>{@code
 * {
 *   "department": {"match": "principal"},
 *   "status": {"equals": "active"},
 *   "region": {"in": ["US", "EU"]}
 * }
 * }</pre>
 */
public record ScopeFilter(Map<String, AttributeFilter> filters) {

    /**
     * Evaluates whether the resource satisfies all filter dimensions given the principal.
     *
     * @param principal the requesting principal's attributes
     * @param resource the target resource's attributes
     * @return true if all dimensions match
     */
    public boolean matches(AttributeSource principal, AttributeSource resource) {
        if (filters.isEmpty()) {
            return true;
        }
        return filters.entrySet().stream()
                .allMatch(entry -> entry.getValue().matches(entry.getKey(), principal, resource));
    }

    /**
     * Parses a scope filter from a JSON object where each key is a dimension name.
     */
    public static ScopeFilter fromJson(JsonNode node) {
        Map<String, AttributeFilter> filters = new HashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            filters.put(entry.getKey(), AttributeFilter.fromJson(entry.getValue()));
        }
        return new ScopeFilter(Map.copyOf(filters));
    }
}
