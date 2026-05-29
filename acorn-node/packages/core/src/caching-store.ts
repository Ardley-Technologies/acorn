import type { PermissionSet } from './permission-set.js';
import type { PermissionLoader, PermissionStore } from './permission-store.js';

export interface CachingStoreOptions {
  ttlMs: number;
  maxSize: number;
}

interface CacheEntry {
  value: PermissionSet;
  expiresAt: number;
  key: string;
  prev: CacheEntry | null;
  next: CacheEntry | null;
}

export class CachingPermissionStore implements PermissionStore {
  private readonly loader: PermissionLoader;
  private readonly ttlMs: number;
  private readonly maxSize: number;
  private readonly map = new Map<string, CacheEntry>();
  private head: CacheEntry | null = null;
  private tail: CacheEntry | null = null;

  constructor(loader: PermissionLoader, options: CachingStoreOptions) {
    this.loader = loader;
    this.ttlMs = options.ttlMs;
    this.maxSize = options.maxSize;
  }

  async getPermissionSet(key: string[]): Promise<PermissionSet | undefined> {
    const cacheKey = key.join('::');

    const entry = this.map.get(cacheKey);
    if (entry !== undefined) {
      if (Date.now() < entry.expiresAt) {
        this.moveToHead(entry);
        return entry.value;
      }
      this.removeEntry(entry);
    }

    const loaded = await this.loader.load(key);
    if (loaded !== undefined) {
      this.put(cacheKey, loaded);
    }
    return loaded;
  }

  invalidate(key: string[]): void {
    const cacheKey = key.join('::');
    const entry = this.map.get(cacheKey);
    if (entry !== undefined) {
      this.removeEntry(entry);
    }
  }

  private put(cacheKey: string, value: PermissionSet): void {
    if (this.map.size >= this.maxSize) {
      this.evictTail();
    }

    const entry: CacheEntry = {
      value,
      expiresAt: Date.now() + this.ttlMs,
      key: cacheKey,
      prev: null,
      next: this.head,
    };

    if (this.head !== null) {
      this.head.prev = entry;
    }
    this.head = entry;
    if (this.tail === null) {
      this.tail = entry;
    }

    this.map.set(cacheKey, entry);
  }

  private moveToHead(entry: CacheEntry): void {
    if (entry === this.head) return;
    this.detach(entry);
    entry.prev = null;
    entry.next = this.head;
    if (this.head !== null) {
      this.head.prev = entry;
    }
    this.head = entry;
    if (this.tail === null) {
      this.tail = entry;
    }
  }

  private removeEntry(entry: CacheEntry): void {
    this.detach(entry);
    this.map.delete(entry.key);
  }

  private evictTail(): void {
    if (this.tail === null) return;
    this.removeEntry(this.tail);
  }

  private detach(entry: CacheEntry): void {
    if (entry.prev !== null) {
      entry.prev.next = entry.next;
    } else {
      this.head = entry.next;
    }
    if (entry.next !== null) {
      entry.next.prev = entry.prev;
    } else {
      this.tail = entry.prev;
    }
    entry.prev = null;
    entry.next = null;
  }
}
