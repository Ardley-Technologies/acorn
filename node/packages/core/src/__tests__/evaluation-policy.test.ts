import { describe, test, expect } from 'bun:test';
import { EvaluationPolicy } from '../evaluation-policy.js';
import { Attributes } from '../attributes.js';

describe('EvaluationPolicy', () => {
  test('none() passes any combination', () => {
    const policy = EvaluationPolicy.none();
    const principal = Attributes.from({ tenant_id: 'a' });
    const resource = Attributes.from({ tenant_id: 'b' });
    expect(policy.checkIsolation(principal, resource)).toBeUndefined();
  });

  test('withIsolation denies mismatched attribute', () => {
    const policy = EvaluationPolicy.withIsolation('tenant_id');
    const principal = Attributes.from({ tenant_id: 'a' });
    const resource = Attributes.from({ tenant_id: 'b' });
    const result = policy.checkIsolation(principal, resource);
    expect(result).toContain('Isolation violation');
    expect(result).toContain("principal='a'");
    expect(result).toContain("resource='b'");
  });

  test('passes when resource lacks isolation attribute', () => {
    const policy = EvaluationPolicy.withIsolation('tenant_id');
    const principal = Attributes.from({ tenant_id: 'a' });
    const resource = Attributes.from({ other: 'x' });
    expect(policy.checkIsolation(principal, resource)).toBeUndefined();
  });

  test('denies when resource has attr but principal lacks it', () => {
    const policy = EvaluationPolicy.withIsolation('tenant_id');
    const principal = Attributes.from({});
    const resource = Attributes.from({ tenant_id: 'b' });
    const result = policy.checkIsolation(principal, resource);
    expect(result).toContain('principal lacks it');
  });

  test('passes when both match', () => {
    const policy = EvaluationPolicy.withIsolation('tenant_id');
    const principal = Attributes.from({ tenant_id: 'same' });
    const resource = Attributes.from({ tenant_id: 'same' });
    expect(policy.checkIsolation(principal, resource)).toBeUndefined();
  });

  test('builder pattern', () => {
    const policy = EvaluationPolicy.builder()
      .withIsolation('tenant_id')
      .withIsolation('org_id')
      .build();
    expect(policy.getIsolationAttributes()).toEqual(['tenant_id', 'org_id']);
  });

  test('strict mode denies when resource lacks isolation attribute', () => {
    const policy = EvaluationPolicy.withIsolation('tenant_id').strict();
    const principal = Attributes.from({ tenant_id: 'a' });
    const resource = Attributes.from({ other: 'x' });
    const result = policy.checkIsolation(principal, resource);
    expect(result).toContain('strict mode');
    expect(result).toContain("'tenant_id'");
  });

  test('strict mode still passes when both match', () => {
    const policy = EvaluationPolicy.withStrictIsolation('tenant_id');
    const principal = Attributes.from({ tenant_id: 'same' });
    const resource = Attributes.from({ tenant_id: 'same' });
    expect(policy.checkIsolation(principal, resource)).toBeUndefined();
  });

  test('strict mode denies mismatched attribute (unchanged from lenient)', () => {
    const policy = EvaluationPolicy.withStrictIsolation('tenant_id');
    const principal = Attributes.from({ tenant_id: 'a' });
    const resource = Attributes.from({ tenant_id: 'b' });
    const result = policy.checkIsolation(principal, resource);
    expect(result).toContain('Isolation violation');
    expect(result).toContain("principal='a'");
  });

  test('strict mode reports the first missing attribute across a multi-attribute policy', () => {
    const policy = EvaluationPolicy.builder()
      .withIsolation('tenant_id')
      .withIsolation('org_id')
      .strict()
      .build();
    const principal = Attributes.from({ tenant_id: 'a', org_id: 'o' });
    const resource = Attributes.from({ tenant_id: 'a' });
    const result = policy.checkIsolation(principal, resource);
    expect(result).toContain('strict mode');
    expect(result).toContain("'org_id'");
  });

  test('isStrict reflects mode', () => {
    expect(EvaluationPolicy.withIsolation('tenant_id').isStrict()).toBe(false);
    expect(EvaluationPolicy.withStrictIsolation('tenant_id').isStrict()).toBe(true);
    expect(EvaluationPolicy.builder().withIsolation('tenant_id').strict().build().isStrict()).toBe(true);
  });
});
