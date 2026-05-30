package com.ardley.acorn.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ardley.acorn.attribute.Attributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Edge case coverage for each AttributeFilter variant.
 * Focuses on missing attributes, empty inputs, and boundary conditions
 * that the happy-path ScopeFilterTest doesn't cover.
 */
class AttributeFilterEdgeCasesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("MatchPrincipal edge cases")
    class MatchPrincipalEdges {

        @Test
        @DisplayName("Fails when principal lacks the dimension attribute")
        void principalMissingAttribute() {
            var filter = new AttributeFilter.MatchPrincipal();
            var principal = Attributes.empty();
            var resource = Attributes.builder().with("department", "Eng").build();

            assertThat(filter.matches("department", principal, resource)).isFalse();
        }

        @Test
        @DisplayName("Fails when both principal and resource lack the attribute")
        void bothMissingAttribute() {
            var filter = new AttributeFilter.MatchPrincipal();
            var principal = Attributes.empty();
            var resource = Attributes.empty();

            assertThat(filter.matches("department", principal, resource)).isFalse();
        }
    }

    @Nested
    @DisplayName("MatchPrincipalAttribute edge cases")
    class MatchPrincipalAttributeEdges {

        @Test
        @DisplayName("Fails when principal lacks the referenced attribute")
        void principalMissingReferencedAttribute() {
            var filter = new AttributeFilter.MatchPrincipalAttribute("user_id");
            var principal = Attributes.empty();
            var resource = Attributes.builder().with("assigned_to", "u-1").build();

            assertThat(filter.matches("assigned_to", principal, resource)).isFalse();
        }

        @Test
        @DisplayName("Fails when values differ")
        void valuesDiffer() {
            var filter = new AttributeFilter.MatchPrincipalAttribute("user_id");
            var principal = Attributes.builder().with("user_id", "u-1").build();
            var resource = Attributes.builder().with("assigned_to", "u-99").build();

            assertThat(filter.matches("assigned_to", principal, resource)).isFalse();
        }

        @Test
        @DisplayName("Fails when resource lacks the dimension attribute")
        void resourceMissingDimension() {
            var filter = new AttributeFilter.MatchPrincipalAttribute("user_id");
            var principal = Attributes.builder().with("user_id", "u-1").build();
            var resource = Attributes.empty();

            assertThat(filter.matches("assigned_to", principal, resource)).isFalse();
        }
    }

    @Nested
    @DisplayName("MatchPrincipalWithFallbacks edge cases")
    class FallbackEdges {

        @Test
        @DisplayName("Fails when resource lacks the dimension attribute")
        void resourceMissingDimension() {
            var filter = new AttributeFilter.MatchPrincipalWithFallbacks(java.util.List.of("user_id", "email"));
            var principal = Attributes.builder().with("user_id", "u-1").with("email", "a@b.com").build();
            var resource = Attributes.empty();

            assertThat(filter.matches("owner", principal, resource)).isFalse();
        }

        @Test
        @DisplayName("Fails when principal has none of the listed attributes")
        void principalMissingAllFallbacks() {
            var filter = new AttributeFilter.MatchPrincipalWithFallbacks(java.util.List.of("user_id", "email"));
            var principal = Attributes.empty();
            var resource = Attributes.builder().with("owner", "u-1").build();

            assertThat(filter.matches("owner", principal, resource)).isFalse();
        }

        @Test
        @DisplayName("Skips non-matching first attribute and matches on second")
        void skipsNonMatchingFirst() {
            var filter = new AttributeFilter.MatchPrincipalWithFallbacks(java.util.List.of("user_id", "email"));
            var principal = Attributes.builder().with("user_id", "u-WRONG").with("email", "target@co.com").build();
            var resource = Attributes.builder().with("owner", "target@co.com").build();

            assertThat(filter.matches("owner", principal, resource)).isTrue();
        }
    }

    @Nested
    @DisplayName("Equals edge cases")
    class EqualsEdges {

        @Test
        @DisplayName("Fails when resource lacks the attribute entirely")
        void resourceMissingAttribute() {
            var filter = new AttributeFilter.Equals("active");
            var resource = Attributes.empty();

            assertThat(filter.matches("status", Attributes.empty(), resource)).isFalse();
        }

        @Test
        @DisplayName("Case-sensitive comparison")
        void caseSensitive() {
            var filter = new AttributeFilter.Equals("Active");
            var resource = Attributes.builder().with("status", "active").build();

            assertThat(filter.matches("status", Attributes.empty(), resource)).isFalse();
        }
    }

    @Nested
    @DisplayName("InList edge cases")
    class InListEdges {

        @Test
        @DisplayName("Empty list never matches")
        void emptyListNeverMatches() {
            var filter = new AttributeFilter.InList(java.util.List.of());
            var resource = Attributes.builder().with("region", "US").build();

            assertThat(filter.matches("region", Attributes.empty(), resource)).isFalse();
        }

        @Test
        @DisplayName("Fails when resource lacks the attribute")
        void resourceMissingAttribute() {
            var filter = new AttributeFilter.InList(java.util.List.of("US", "EU"));
            var resource = Attributes.empty();

            assertThat(filter.matches("region", Attributes.empty(), resource)).isFalse();
        }

        @Test
        @DisplayName("Case-sensitive list membership")
        void caseSensitive() {
            var filter = new AttributeFilter.InList(java.util.List.of("US", "EU"));
            var resource = Attributes.builder().with("region", "us").build();

            assertThat(filter.matches("region", Attributes.empty(), resource)).isFalse();
        }
    }

    @Nested
    @DisplayName("JSON parsing edge cases")
    class JsonParsing {

        @Test
        @DisplayName("Unrecognized filter structure throws")
        void unrecognizedFilterThrows() throws Exception {
            var node = MAPPER.readTree("""
                {"unknownKey": "unknownValue"}
                """);

            assertThatThrownBy(() -> AttributeFilter.fromJson(node))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unrecognized attribute filter");
        }

        @Test
        @DisplayName("matchPrincipalAttributes with empty array parses but never matches")
        void emptyFallbackArrayParsesButNeverMatches() throws Exception {
            var node = MAPPER.readTree("""
                {"matchPrincipalAttributes": []}
                """);
            var filter = AttributeFilter.fromJson(node);

            var resource = Attributes.builder().with("owner", "anyone").build();
            assertThat(filter.matches("owner", Attributes.empty(), resource)).isFalse();
        }
    }
}
