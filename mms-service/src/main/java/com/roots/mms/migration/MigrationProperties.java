package com.roots.mms.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime configuration for the Mongo → Postgres migration tool.
 *
 * <p>Bound via {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * from {@link MigrationConfig}. The bean is only created under the
 * {@code migration} Spring profile.
 *
 * <h3>Properties</h3>
 * <ul>
 *     <li>{@code migration.mode} — one of {@code dry-run | migrate | verify}.
 *         Defaults to {@code dry-run} so accidental invocations never write
 *         to Postgres.</li>
 *     <li>{@code migration.batch-size} — size of the chunks used when paging
 *         through large Mongo collections and when batch-flushing JPA writes.
 *         Defaults to 500.</li>
 *     <li>{@code migration.skip-seeding} — when {@code true}, the
 *         {@code DataInitializer} runner is suppressed so the migration owns
 *         the write path end-to-end. Defaults to {@code true}.</li>
 * </ul>
 */
@ConfigurationProperties("migration")
public class MigrationProperties {

    /** dry-run | migrate | verify. */
    private String mode = "dry-run";

    /** Mongo page size + JPA save-and-flush chunk size. */
    private int batchSize = 500;

    /** When true, DataInitializer seeding is skipped in the migration profile. */
    private boolean skipSeeding = true;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isSkipSeeding() {
        return skipSeeding;
    }

    public void setSkipSeeding(boolean skipSeeding) {
        this.skipSeeding = skipSeeding;
    }
}
