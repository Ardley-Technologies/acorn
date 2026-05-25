package com.ardley.acorn.store;

import com.ardley.acorn.permission.PermissionSet;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * A caching decorator for any {@link PermissionLoader}.
 *
 * <p>Wraps a loader with a Guava in-memory cache, providing configurable TTL and
 * maximum capacity. This is the recommended way to integrate a database-backed
 * permission loader without incurring storage I/O on every authorization check.
 *
 * <pre>{@code
 * PermissionLoader loader = new PersistencePermissionLoader(client);
 * PermissionStore store = new CachingPermissionStore(loader, Duration.ofMinutes(5), 10_000);
 * }</pre>
 */
public final class CachingPermissionStore implements PermissionStore {

    private final PermissionLoader loader;
    private final Cache<String, PermissionSet> cache;

    public CachingPermissionStore(PermissionLoader loader, Duration ttl, long maxCapacity) {
        this.loader = loader;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxCapacity)
                .recordStats()
                .build();
    }

    @Override
    public Optional<PermissionSet> getPermissionSet(List<String> key) {
        String cacheKey = String.join("::", key);

        PermissionSet cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<PermissionSet> loaded = loader.load(key);
        loaded.ifPresent(ps -> cache.put(cacheKey, ps));
        return loaded;
    }

    @Override
    public void invalidate(List<String> key) {
        cache.invalidate(String.join("::", key));
    }
}
