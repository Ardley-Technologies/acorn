package com.ardley.acorn.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests role configuration validation including JSON parsing, hierarchy enforcement,
 * and structural correctness checks.
 */
class RoleConfigurationValidatorTest {

    @Test
    @DisplayName("Valid permission JSON passes validation")
    void validJsonPasses() {
        var validator = RoleConfigurationValidator.builder().build();

        var result = validator.validate("""
            {"allow": {"ListUsers": "all", "UpdateUser": {"department": {"match": "principal"}}}}
            """);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Malformed JSON produces validation error")
    void malformedJsonFails() {
        var validator = RoleConfigurationValidator.builder().build();

        var result = validator.validate("not valid json {{");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Invalid permission JSON"));
    }

    @Test
    @DisplayName("Hierarchy violation: edit granted at 'all' without view")
    void hierarchyViolationEditWithoutView() {
        var validator = RoleConfigurationValidator.builder()
                .withHierarchy("UpdateUser", "ViewUser")
                .build();

        var result = validator.validate("""
            {"allow": {"UpdateUser": "all"}}
            """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e ->
                e.contains("Hierarchy violation") && e.contains("UpdateUser") && e.contains("ViewUser"));
    }

    @Test
    @DisplayName("Hierarchy satisfied: both edit and view granted")
    void hierarchySatisfied() {
        var validator = RoleConfigurationValidator.builder()
                .withHierarchy("UpdateUser", "ViewUser")
                .build();

        var result = validator.validate("""
            {"allow": {"UpdateUser": "all", "ViewUser": "all"}}
            """);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Hierarchy not triggered when higher action is absent")
    void hierarchyNotTriggeredWhenHigherAbsent() {
        var validator = RoleConfigurationValidator.builder()
                .withHierarchy("UpdateUser", "ViewUser")
                .build();

        var result = validator.validate("""
            {"allow": {"ViewUser": "all"}}
            """);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Allow all shorthand passes validation")
    void allowAllShorthandPasses() {
        var validator = RoleConfigurationValidator.builder()
                .withHierarchy("UpdateUser", "ViewUser")
                .build();

        var result = validator.validate("""
            {"allow": "all"}
            """);

        assertThat(result.isValid()).isTrue();
    }
}
