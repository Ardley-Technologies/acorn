package com.ardley.acorn.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.ardley.acorn.permission.PermissionSet;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the caching permission store behavior: cache hits, misses, invalidation,
 * and delegation to the underlying loader.
 */
class CachingPermissionStoreTest {

    @Test
    @DisplayName("First call delegates to loader and caches result")
    void firstCallDelegatesToLoader() {
        var callCount = new AtomicInteger(0);
        PermissionLoader loader = key -> {
            callCount.incrementAndGet();
            return Optional.of(PermissionSet.allowAll());
        };

        CachingPermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 100);

        Optional<PermissionSet> result = store.getPermissionSet(List.of("tenant-1", "admin"));

        assertThat(result).isPresent();
        assertThat(result.get().isAllowAll()).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Second call serves from cache without hitting loader")
    void secondCallServesFromCache() {
        var callCount = new AtomicInteger(0);
        PermissionLoader loader = key -> {
            callCount.incrementAndGet();
            return Optional.of(PermissionSet.allowAll());
        };

        CachingPermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 100);
        var key = List.of("tenant-1", "admin");

        store.getPermissionSet(key);
        store.getPermissionSet(key);
        store.getPermissionSet(key);

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Different keys produce independent cache entries")
    void differentKeysIndependent() {
        var callCount = new AtomicInteger(0);
        PermissionLoader loader = key -> {
            callCount.incrementAndGet();
            if (key.contains("admin")) return Optional.of(PermissionSet.allowAll());
            return Optional.of(PermissionSet.empty());
        };

        CachingPermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 100);

        var admin = store.getPermissionSet(List.of("t-1", "admin"));
        var viewer = store.getPermissionSet(List.of("t-1", "viewer"));

        assertThat(admin.get().isAllowAll()).isTrue();
        assertThat(viewer.get().isAllowAll()).isFalse();
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Invalidation forces next call to hit loader again")
    void invalidationForcesReload() {
        var callCount = new AtomicInteger(0);
        PermissionLoader loader = key -> {
            callCount.incrementAndGet();
            return Optional.of(PermissionSet.allowAll());
        };

        CachingPermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 100);
        var key = List.of("tenant-1", "admin");

        store.getPermissionSet(key);
        assertThat(callCount.get()).isEqualTo(1);

        store.invalidate(key);
        store.getPermissionSet(key);

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Loader returning empty is not cached (miss on every call)")
    void emptyResultNotCached() {
        var callCount = new AtomicInteger(0);
        PermissionLoader loader = key -> {
            callCount.incrementAndGet();
            return Optional.empty();
        };

        CachingPermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 100);
        var key = List.of("unknown-tenant", "unknown-role");

        store.getPermissionSet(key);
        store.getPermissionSet(key);

        assertThat(callCount.get()).isEqualTo(2);
    }
}
