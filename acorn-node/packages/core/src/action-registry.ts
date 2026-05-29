import type { Action } from './types.js';

export class ActionRegistry {
  private readonly actions = new Map<string, Action>();

  register(action: Action): void {
    if (this.actions.has(action.name)) {
      throw new Error(`Duplicate action name: "${action.name}"`);
    }
    this.actions.set(action.name, action);
  }

  registerAll(actions: Record<string, Action>): void {
    for (const action of Object.values(actions)) {
      this.register(action);
    }
  }

  resolve(name: string): Action {
    const action = this.actions.get(name);
    if (action === undefined) {
      throw new Error(`Unknown action: "${name}"`);
    }
    return action;
  }

  all(): Action[] {
    return [...this.actions.values()];
  }

  size(): number {
    return this.actions.size;
  }
}
