package com.ardley.acorn.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests PermissionSet JSON parsing including shorthand formats, granular configurations,
 * and error handling for malformed input.
 */
class PermissionSetTest {

    @Test
    @DisplayName("Parses 'allow all' shorthand into allowAll flag")
    void parseAllowAllShorthand() {
        PermissionSet ps = PermissionSet.fromJson("""
            {"allow": "all"}
            """);

        assertThat(ps.isAllowAll()).isTrue();
        assertThat(ps.hasAllowFor("anything")).isTrue();
        assertThat(ps.hasUnconditionalDeny("anything")).isFalse();
    }

    @Test
    @DisplayName("Parses granular allow and deny maps")
    void parseGranularPermissions() {
        PermissionSet ps = PermissionSet.fromJson("""
            {
                "allow": {
                    "ListUsers": "all",
                    "UpdateUser": {"department": {"match": "principal"}}
                },
                "deny": {
                    "DeleteUser": "all"
                }
            }
            """);

        assertThat(ps.isAllowAll()).isFalse();
        assertThat(ps.hasAllowFor("ListUsers")).isTrue();
        assertThat(ps.hasAllowFor("UpdateUser")).isTrue();
        assertThat(ps.hasAllowFor("CreateUser")).isFalse();
        assertThat(ps.hasUnconditionalDeny("DeleteUser")).isTrue();
        assertThat(ps.hasUnconditionalDeny("UpdateUser")).isFalse();
    }

    @Test
    @DisplayName("Allow level returns correct PermissionLevel type")
    void allowLevelTypes() {
        PermissionSet ps = PermissionSet.fromJson("""
            {
                "allow": {
                    "ListUsers": "all",
                    "UpdateUser": {"department": {"match": "principal"}},
                    "DeleteUser": "none"
                }
            }
            """);

        assertThat(ps.allowLevel("ListUsers")).isPresent().get().matches(PermissionLevel::isAll);
        assertThat(ps.allowLevel("UpdateUser")).isPresent().get().matches(PermissionLevel::isScoped);
        assertThat(ps.allowLevel("DeleteUser")).isPresent().get().matches(PermissionLevel::isNone);
        assertThat(ps.allowLevel("Unknown")).isEmpty();
    }

    @Test
    @DisplayName("Deny with allow all: deny entries override for specific actions")
    void denyWithAllowAll() {
        PermissionSet ps = PermissionSet.fromJson("""
            {
                "allow": "all",
                "deny": {"DeleteUser": "all", "SuspendUser": "all"}
            }
            """);

        assertThat(ps.isAllowAll()).isTrue();
        assertThat(ps.hasUnconditionalDeny("DeleteUser")).isTrue();
        assertThat(ps.hasUnconditionalDeny("SuspendUser")).isTrue();
        assertThat(ps.hasUnconditionalDeny("UpdateUser")).isFalse();
    }

    @Test
    @DisplayName("Empty JSON produces empty permission set that denies everything")
    void emptyJsonDeniesAll() {
        PermissionSet ps = PermissionSet.fromJson("{}");

        assertThat(ps.isAllowAll()).isFalse();
        assertThat(ps.hasAllowFor("anything")).isFalse();
    }

    @Test
    @DisplayName("Malformed JSON throws IllegalArgumentException")
    void malformedJsonThrows() {
        assertThatThrownBy(() -> PermissionSet.fromJson("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid permission JSON");
    }

    @Test
    @DisplayName("Invalid permission level string throws")
    void invalidLevelStringThrows() {
        assertThatThrownBy(() -> PermissionSet.fromJson("""
            {"allow": {"ListUsers": "maybe"}}
            """))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
