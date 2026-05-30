import type { PermissionLevel } from './permission-level.js';
import { parsePermissionLevel } from './permission-level.js';

export class PermissionSet {
  private constructor(
    private readonly _allowAll: boolean,
    private readonly allow: ReadonlyMap<string, PermissionLevel>,
    private readonly deny: ReadonlyMap<string, PermissionLevel>,
  ) {}

  static allowAll(): PermissionSet {
    return new PermissionSet(true, new Map(), new Map());
  }

  static empty(): PermissionSet {
    return new PermissionSet(false, new Map(), new Map());
  }

  static fromJson(json: string): PermissionSet {
    try {
      const root = JSON.parse(json);
      return PermissionSet.fromObject(root);
    } catch (e) {
      if (e instanceof Error && e.message.startsWith('Invalid permission')) throw e;
      throw new Error(`Invalid permission JSON: ${(e as Error).message}`);
    }
  }

  static fromObject(root: unknown): PermissionSet {
    if (root === null || typeof root !== 'object') {
      throw new Error('Permission set must be an object');
    }
    const obj = root as Record<string, unknown>;
    const allowNode = obj.allow;
    const denyNode = obj.deny;

    if (typeof allowNode === 'string' && allowNode === 'all') {
      return new PermissionSet(true, new Map(), parseLevelMap(denyNode));
    }

    return new PermissionSet(false, parseLevelMap(allowNode), parseLevelMap(denyNode));
  }

  isAllowAll(): boolean {
    return this._allowAll;
  }

  hasAllowFor(actionName: string): boolean {
    if (this._allowAll) return true;
    const level = this.allow.get(actionName);
    return level !== undefined && level.type !== 'none';
  }

  hasUnconditionalDeny(actionName: string): boolean {
    const level = this.deny.get(actionName);
    return level !== undefined && level.type === 'all';
  }

  allowLevel(actionName: string): PermissionLevel | undefined {
    return this.allow.get(actionName);
  }

  denyLevel(actionName: string): PermissionLevel | undefined {
    return this.deny.get(actionName);
  }
}

function parseLevelMap(node: unknown): Map<string, PermissionLevel> {
  if (node === null || node === undefined) return new Map();
  if (typeof node !== 'object') throw new Error('Level map must be an object');
  const map = new Map<string, PermissionLevel>();
  for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
    map.set(key, parsePermissionLevel(value));
  }
  return map;
}
