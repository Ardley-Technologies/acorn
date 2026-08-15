import type { AttributeSource } from './types.js';

/**
 * Declares which principal/resource attributes must match at evaluation time.
 *
 * The classic use is tenant isolation: `withIsolation('tenant_id')` denies
 * evaluation as soon as a loaded resource carries a different `tenant_id`
 * than the principal. Isolation is checked before permission rules — see
 * the evaluator contract in `evaluator.ts`.
 *
 * Isolation runs only inside `evaluate()`. Gate checks
 * (`canPerformAction`) have no resource to compare against, so the policy
 * does not apply to them.
 *
 * ### Strict mode
 *
 * By default, if a loaded resource omits the isolation attribute the check
 * silently passes (there is nothing to compare). This is intentional for
 * apps where only some resources are tenant-scoped.
 *
 * When every resource under a policy MUST carry the isolation attribute,
 * enable strict mode with `.strict()`. A resource missing the attribute
 * then produces an isolation violation — protecting against a
 * `ResourceExtractor.attributes()` implementation that silently forgets to
 * include the isolation key.
 */
export class EvaluationPolicy {
  private readonly isolationAttributes: readonly string[];
  private readonly strictMode: boolean;

  private constructor(isolationAttributes: string[], strictMode: boolean) {
    this.isolationAttributes = Object.freeze([...isolationAttributes]);
    this.strictMode = strictMode;
  }

  static none(): EvaluationPolicy {
    return new EvaluationPolicy([], false);
  }

  static withIsolation(...attributes: string[]): EvaluationPolicy {
    return new EvaluationPolicy(attributes, false);
  }

  static withStrictIsolation(...attributes: string[]): EvaluationPolicy {
    return new EvaluationPolicy(attributes, true);
  }

  static builder(): EvaluationPolicyBuilder {
    return new EvaluationPolicyBuilder();
  }

  /**
   * Returns a copy of this policy with strict mode enabled. In strict mode,
   * a resource that omits any declared isolation attribute is treated as an
   * isolation violation rather than a silent pass.
   */
  strict(): EvaluationPolicy {
    return new EvaluationPolicy([...this.isolationAttributes], true);
  }

  isStrict(): boolean {
    return this.strictMode;
  }

  checkIsolation(principal: AttributeSource, resource: AttributeSource): string | undefined {
    for (const attr of this.isolationAttributes) {
      const resourceVal = resource.attribute(attr);
      const principalVal = principal.attribute(attr);

      if (resourceVal !== undefined && principalVal !== undefined && resourceVal !== principalVal) {
        return `Isolation violation on '${attr}': principal='${principalVal}', resource='${resourceVal}'`;
      }

      if (resourceVal !== undefined && principalVal === undefined) {
        return `Isolation violation: resource has '${attr}=${resourceVal}' but principal lacks it`;
      }

      if (this.strictMode && resourceVal === undefined) {
        return `Isolation violation: strict mode requires resource to declare '${attr}' but attribute is missing`;
      }
    }
    return undefined;
  }

  getIsolationAttributes(): readonly string[] {
    return this.isolationAttributes;
  }
}

class EvaluationPolicyBuilder {
  private attrs: string[] = [];
  private strictMode = false;

  withIsolation(attributeName: string): this {
    this.attrs.push(attributeName);
    return this;
  }

  strict(): this {
    this.strictMode = true;
    return this;
  }

  build(): EvaluationPolicy {
    const policy = EvaluationPolicy.withIsolation(...this.attrs);
    return this.strictMode ? policy.strict() : policy;
  }
}
