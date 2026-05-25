package com.ardley.acorn.policy;

import com.ardley.acorn.attribute.Attributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests isolation policy enforcement logic across various attribute configurations.
 */
class EvaluationPolicyTest {

    @Test
    @DisplayName("Matching isolation attribute passes")
    void matchingAttributePasses() {
        EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");
        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("tenant_id", "t-1").build();

        assertThat(policy.checkIsolation(principal, resource)).isEmpty();
    }

    @Test
    @DisplayName("Mismatched isolation attribute produces violation")
    void mismatchedAttributeViolates() {
        EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");
        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("tenant_id", "t-2").build();

        Optional<String> result = policy.checkIsolation(principal, resource);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Isolation violation").contains("tenant_id");
    }

    @Test
    @DisplayName("Resource lacking isolation attribute is treated as unscoped (passes)")
    void resourceWithoutAttributePasses() {
        EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");
        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var resource = Attributes.empty();

        assertThat(policy.checkIsolation(principal, resource)).isEmpty();
    }

    @Test
    @DisplayName("Principal lacking isolation attribute when resource has it is a violation")
    void principalMissingAttributeViolates() {
        EvaluationPolicy policy = EvaluationPolicy.withIsolation("tenant_id");
        var principal = Attributes.empty();
        var resource = Attributes.builder().with("tenant_id", "t-1").build();

        Optional<String> result = policy.checkIsolation(principal, resource);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("principal lacks it");
    }

    @Test
    @DisplayName("No isolation policy performs no checks regardless of attributes")
    void noIsolationPerformsNoChecks() {
        EvaluationPolicy policy = EvaluationPolicy.none();
        var principal = Attributes.builder().with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("tenant_id", "t-DIFFERENT").build();

        assertThat(policy.checkIsolation(principal, resource)).isEmpty();
    }

    @Test
    @DisplayName("Multiple isolation attributes: first violation short-circuits")
    void multipleIsolationAttributesFirstViolationFails() {
        EvaluationPolicy policy = EvaluationPolicy.builder()
                .withIsolation("org_id")
                .withIsolation("tenant_id")
                .build();
        var principal = Attributes.builder().with("org_id", "org-X").with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("org_id", "org-Y").with("tenant_id", "t-1").build();

        Optional<String> result = policy.checkIsolation(principal, resource);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("org_id");
    }

    @Test
    @DisplayName("Multiple isolation attributes: all must match")
    void multipleIsolationAllMustMatch() {
        EvaluationPolicy policy = EvaluationPolicy.builder()
                .withIsolation("org_id")
                .withIsolation("tenant_id")
                .build();
        var principal = Attributes.builder().with("org_id", "org-1").with("tenant_id", "t-1").build();
        var resource = Attributes.builder().with("org_id", "org-1").with("tenant_id", "t-1").build();

        assertThat(policy.checkIsolation(principal, resource)).isEmpty();
    }
}
