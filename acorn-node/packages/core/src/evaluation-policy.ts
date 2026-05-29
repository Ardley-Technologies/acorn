import type { AttributeSource } from './types.js';

export class EvaluationPolicy {
  private readonly isolationAttributes: readonly string[];

  private constructor(isolationAttributes: string[]) {
    this.isolationAttributes = Object.freeze([...isolationAttributes]);
  }

  static none(): EvaluationPolicy {
    return new EvaluationPolicy([]);
  }

  static withIsolation(...attributes: string[]): EvaluationPolicy {
    return new EvaluationPolicy(attributes);
  }

  static builder(): EvaluationPolicyBuilder {
    return new EvaluationPolicyBuilder();
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
    }
    return undefined;
  }

  getIsolationAttributes(): readonly string[] {
    return this.isolationAttributes;
  }
}

class EvaluationPolicyBuilder {
  private attrs: string[] = [];

  withIsolation(attributeName: string): this {
    this.attrs.push(attributeName);
    return this;
  }

  build(): EvaluationPolicy {
    return EvaluationPolicy.withIsolation(...this.attrs);
  }
}
