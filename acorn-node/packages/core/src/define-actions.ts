import type { Action } from './types.js';

export function defineActions<T extends Record<string, string>>(
  defs: T,
): { [K in keyof T]: Action } {
  const result = {} as Record<string, Action>;
  for (const [name, description] of Object.entries(defs)) {
    result[name] = Object.freeze({ name, description });
  }
  return Object.freeze(result) as { [K in keyof T]: Action };
}
