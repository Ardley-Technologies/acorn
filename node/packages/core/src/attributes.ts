import type { AttributeSource } from './types.js';

export class Attributes implements AttributeSource {
  private readonly entries: ReadonlyMap<string, string>;

  private constructor(entries: Map<string, string>) {
    this.entries = entries;
  }

  attribute(name: string): string | undefined {
    return this.entries.get(name);
  }

  toRecord(): Record<string, string> {
    return Object.fromEntries(this.entries);
  }

  static empty(): Attributes {
    return new Attributes(new Map());
  }

  static from(record: Record<string, string | null | undefined>): Attributes {
    const map = new Map<string, string>();
    for (const [k, v] of Object.entries(record)) {
      if (v != null) map.set(k, v);
    }
    return new Attributes(map);
  }
}
