package com.roots.mms.migration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-run mapping of {@code Mongo _id (String) → new Postgres UUID}, namespaced
 * by collection. Not a Spring bean — an instance is created in
 * {@link MigrationService} and shared across all per-collection copy methods
 * so foreign-key translation is internally consistent for the lifetime of the
 * batch.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}, although the migration tool is
 * intentionally single-threaded per collection — concurrency lives here only
 * to keep contributors from introducing a race by accident.
 *
 * <p>{@link #resolveOrAssign(String, String)} is idempotent: the first call
 * mints a UUID and remembers it; every subsequent call with the same
 * {@code (collection, mongoId)} pair returns the same UUID. That makes FK
 * translation stable regardless of what order documents are visited in.
 */
public final class IdMap {

    private final Map<String, Map<String, UUID>> byCollection = new ConcurrentHashMap<>();

    /**
     * Return the UUID mapped to {@code (collection, mongoId)}, minting a new
     * one if this is the first time the pair has been seen. Never returns null.
     */
    public UUID resolveOrAssign(String collection, String mongoId) {
        return byCollection
                .computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(mongoId, k -> UUID.randomUUID());
    }

    /**
     * Lookup only — returns {@code null} if the pair has not been assigned.
     * Use this when you need to fail fast on an unresolved FK instead of
     * silently minting a new orphan UUID.
     */
    public UUID get(String collection, String mongoId) {
        Map<String, UUID> bucket = byCollection.get(collection);
        return bucket == null ? null : bucket.get(mongoId);
    }

    /** Number of distinct source ids seen for {@code collection}. */
    public int size(String collection) {
        Map<String, UUID> bucket = byCollection.get(collection);
        return bucket == null ? 0 : bucket.size();
    }

    /**
     * Force-set a mapping, replacing any prior {@link #resolveOrAssign}
     * result. Used only when a row is discovered to already exist in
     * Postgres (business-key collision) and we need subsequent FK lookups
     * to resolve to the existing UUID rather than a freshly-minted orphan.
     */
    public void put(String collection, String mongoId, UUID uuid) {
        byCollection
                .computeIfAbsent(collection, k -> new ConcurrentHashMap<>())
                .put(mongoId, uuid);
    }

    /** Total id count across all collections — used for the final summary line. */
    public int totalSize() {
        return byCollection.values().stream().mapToInt(Map::size).sum();
    }
}
