import type { PermissionSet } from './permission-set.js';

export interface PermissionLoader {
  load(key: string[]): Promise<PermissionSet | undefined>;
}

export interface PermissionStore {
  getPermissionSet(key: string[]): Promise<PermissionSet | undefined>;
  invalidate(key: string[]): void;
}
