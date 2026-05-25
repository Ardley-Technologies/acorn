package com.ardley.acorn.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.ardley.acorn.attribute.Attributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests scope filter parsing and evaluation logic.
 * Validates each AttributeFilter variant and multi-dimensional AND semantics.
 */
class ScopeFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("MatchPrincipal: passes when same-named attribute matches")
    void matchPrincipalSameValue() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"department": {"match": "principal"}}
            """);
        var principal = Attributes.builder().with("department", "Engineering").build();
        var resource = Attributes.builder().with("department", "Engineering").build();

        assertThat(filter.matches(principal, resource)).isTrue();
    }

    @Test
    @DisplayName("MatchPrincipal: fails when values differ")
    void matchPrincipalDifferentValue() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"department": {"match": "principal"}}
            """);
        var principal = Attributes.builder().with("department", "Engineering").build();
        var resource = Attributes.builder().with("department", "Sales").build();

        assertThat(filter.matches(principal, resource)).isFalse();
    }

    @Test
    @DisplayName("MatchPrincipal: fails when resource lacks the attribute")
    void matchPrincipalResourceMissing() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"department": {"match": "principal"}}
            """);
        var principal = Attributes.builder().with("department", "Engineering").build();
        var resource = Attributes.empty();

        assertThat(filter.matches(principal, resource)).isFalse();
    }

    @Test
    @DisplayName("MatchPrincipalAttribute: compares resource dimension against a different principal attribute")
    void matchPrincipalAttributeCrossAttribute() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"assigned_to": {"matchPrincipalAttribute": "user_id"}}
            """);
        var principal = Attributes.builder().with("user_id", "u-42").build();
        var resource = Attributes.builder().with("assigned_to", "u-42").build();

        assertThat(filter.matches(principal, resource)).isTrue();
    }

    @Test
    @DisplayName("MatchPrincipalWithFallbacks: first matching attribute wins")
    void fallbacksFirstMatchWins() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"owner": {"matchPrincipalAttributes": ["user_id", "email"]}}
            """);
        var principal = Attributes.builder()
                .with("user_id", "u-42")
                .with("email", "alice@co.com")
                .build();
        var resource = Attributes.builder().with("owner", "u-42").build();

        assertThat(filter.matches(principal, resource)).isTrue();
    }

    @Test
    @DisplayName("MatchPrincipalWithFallbacks: falls through to second attribute")
    void fallbacksSecondMatch() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"owner": {"matchPrincipalAttributes": ["user_id", "email"]}}
            """);
        var principal = Attributes.builder()
                .with("user_id", "u-99")
                .with("email", "alice@co.com")
                .build();
        var resource = Attributes.builder().with("owner", "alice@co.com").build();

        assertThat(filter.matches(principal, resource)).isTrue();
    }

    @Test
    @DisplayName("MatchPrincipalWithFallbacks: no attribute matches returns false")
    void fallbacksNoMatch() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"owner": {"matchPrincipalAttributes": ["user_id", "email"]}}
            """);
        var principal = Attributes.builder()
                .with("user_id", "u-99")
                .with("email", "alice@co.com")
                .build();
        var resource = Attributes.builder().with("owner", "someone-else").build();

        assertThat(filter.matches(principal, resource)).isFalse();
    }

    @Test
    @DisplayName("Equals: resource attribute must match literal value")
    void equalsFilter() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"status": {"equals": "active"}}
            """);
        var principal = Attributes.empty();

        assertThat(filter.matches(principal, Attributes.builder().with("status", "active").build())).isTrue();
        assertThat(filter.matches(principal, Attributes.builder().with("status", "suspended").build())).isFalse();
    }

    @Test
    @DisplayName("InList: resource attribute must be in allowed values")
    void inListFilter() throws Exception {
        ScopeFilter filter = parseFilter("""
            {"region": {"in": ["US", "EU", "APAC"]}}
            """);
        var principal = Attributes.empty();

        assertThat(filter.matches(principal, Attributes.builder().with("region", "EU").build())).isTrue();
        assertThat(filter.matches(principal, Attributes.builder().with("region", "LATAM").build())).isFalse();
    }

    @Test
    @DisplayName("Multiple dimensions: all must match (AND semantics)")
    void multiDimensionAndSemantics() throws Exception {
        ScopeFilter filter = parseFilter("""
            {
                "department": {"match": "principal"},
                "status": {"equals": "active"},
                "region": {"in": ["US", "EU"]}
            }
            """);
        var principal = Attributes.builder().with("department", "Eng").build();

        var allMatch = Attributes.builder()
                .with("department", "Eng").with("status", "active").with("region", "US").build();
        assertThat(filter.matches(principal, allMatch)).isTrue();

        var wrongDept = Attributes.builder()
                .with("department", "Sales").with("status", "active").with("region", "US").build();
        assertThat(filter.matches(principal, wrongDept)).isFalse();

        var wrongStatus = Attributes.builder()
                .with("department", "Eng").with("status", "inactive").with("region", "US").build();
        assertThat(filter.matches(principal, wrongStatus)).isFalse();

        var wrongRegion = Attributes.builder()
                .with("department", "Eng").with("status", "active").with("region", "LATAM").build();
        assertThat(filter.matches(principal, wrongRegion)).isFalse();
    }

    @Test
    @DisplayName("Empty filter matches everything")
    void emptyFilterMatchesAll() throws Exception {
        ScopeFilter filter = parseFilter("{}");
        var principal = Attributes.empty();
        var resource = Attributes.builder().with("anything", "value").build();

        assertThat(filter.matches(principal, resource)).isTrue();
    }

    private ScopeFilter parseFilter(String json) throws Exception {
        return ScopeFilter.fromJson(MAPPER.readTree(json));
    }
}
