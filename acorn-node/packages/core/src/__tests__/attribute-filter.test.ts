import { describe, test, expect } from 'bun:test';
import { matchesFilter, parseAttributeFilter } from '../attribute-filter.js';
import { Attributes } from '../attributes.js';

describe('AttributeFilter', () => {
  describe('matchPrincipal', () => {
    const filter = parseAttributeFilter({ match: 'principal' });

    test('matches when same attribute value', () => {
      const principal = Attributes.from({ department: 'Eng' });
      const resource = Attributes.from({ department: 'Eng' });
      expect(matchesFilter(filter, 'department', principal, resource)).toBe(true);
    });

    test('denies when values differ', () => {
      const principal = Attributes.from({ department: 'Eng' });
      const resource = Attributes.from({ department: 'Sales' });
      expect(matchesFilter(filter, 'department', principal, resource)).toBe(false);
    });

    test('denies when resource lacks attribute', () => {
      const principal = Attributes.from({ department: 'Eng' });
      const resource = Attributes.from({});
      expect(matchesFilter(filter, 'department', principal, resource)).toBe(false);
    });

    test('denies when principal lacks attribute', () => {
      const principal = Attributes.from({});
      const resource = Attributes.from({ department: 'Eng' });
      expect(matchesFilter(filter, 'department', principal, resource)).toBe(false);
    });
  });

  describe('matchPrincipalAttribute', () => {
    const filter = parseAttributeFilter({ matchPrincipalAttribute: 'userId' });

    test('matches when resource attr equals named principal attr', () => {
      const principal = Attributes.from({ userId: 'u-123' });
      const resource = Attributes.from({ owner: 'u-123' });
      expect(matchesFilter(filter, 'owner', principal, resource)).toBe(true);
    });

    test('denies on mismatch', () => {
      const principal = Attributes.from({ userId: 'u-123' });
      const resource = Attributes.from({ owner: 'u-456' });
      expect(matchesFilter(filter, 'owner', principal, resource)).toBe(false);
    });
  });

  describe('matchPrincipalWithFallbacks', () => {
    const filter = parseAttributeFilter({ matchPrincipalAttributes: ['userId', 'email'] });

    test('matches on first attribute', () => {
      const principal = Attributes.from({ userId: 'u-1', email: 'a@b.com' });
      const resource = Attributes.from({ owner: 'u-1' });
      expect(matchesFilter(filter, 'owner', principal, resource)).toBe(true);
    });

    test('falls back to second attribute', () => {
      const principal = Attributes.from({ email: 'a@b.com' });
      const resource = Attributes.from({ owner: 'a@b.com' });
      expect(matchesFilter(filter, 'owner', principal, resource)).toBe(true);
    });

    test('denies when no fallback matches', () => {
      const principal = Attributes.from({ userId: 'u-1', email: 'a@b.com' });
      const resource = Attributes.from({ owner: 'nobody' });
      expect(matchesFilter(filter, 'owner', principal, resource)).toBe(false);
    });
  });

  describe('equals', () => {
    const filter = parseAttributeFilter({ equals: 'active' });

    test('matches literal value', () => {
      const resource = Attributes.from({ status: 'active' });
      expect(matchesFilter(filter, 'status', Attributes.empty(), resource)).toBe(true);
    });

    test('denies on mismatch', () => {
      const resource = Attributes.from({ status: 'suspended' });
      expect(matchesFilter(filter, 'status', Attributes.empty(), resource)).toBe(false);
    });
  });

  describe('inList', () => {
    const filter = parseAttributeFilter({ in: ['US', 'EU'] });

    test('matches value in set', () => {
      const resource = Attributes.from({ region: 'US' });
      expect(matchesFilter(filter, 'region', Attributes.empty(), resource)).toBe(true);
    });

    test('denies value not in set', () => {
      const resource = Attributes.from({ region: 'APAC' });
      expect(matchesFilter(filter, 'region', Attributes.empty(), resource)).toBe(false);
    });
  });

  describe('parseAttributeFilter', () => {
    test('throws on unrecognized filter', () => {
      expect(() => parseAttributeFilter({ unknown: true })).toThrow('Unrecognized');
    });

    test('throws on non-object', () => {
      expect(() => parseAttributeFilter(null)).toThrow();
    });
  });
});
