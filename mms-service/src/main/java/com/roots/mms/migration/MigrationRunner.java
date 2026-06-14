package com.roots.mms.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line entry point for the migration tool. Active only under the
 * {@code migration} Spring profile.
 *
 * <p>Reads {@link MigrationProperties#getMode()} to decide which pass to run:
 * <ul>
 *     <li>{@code dry-run} — count + validate only, never writes.</li>
 *     <li>{@code migrate} — copies Mongo documents into Postgres.</li>
 *     <li>{@code verify} — reads both sides and reports deltas / sample mismatches.</li>
 * </ul>
 *
 * <p>Always calls {@link System#exit(int)} when finished so the Spring Boot
 * app terminates cleanly — the migration profile is a batch process, not a
 * web server. A failure inside the dispatch (uncaught exception or any
 * collection reporting failed writes) maps to a non-zero exit code so an
 * operator running it under supervision sees the process fail.
 */
@Component
@Profile("migration")
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final MigrationService migrationService;
    private final MigrationProperties properties;

    public MigrationRunner(MigrationService migrationService, MigrationProperties properties) {
        this.migrationService = migrationService;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        int exitCode = 0;
        try {
            String mode = properties.getMode() == null ? "dry-run" : properties.getMode().trim().toLowerCase();
            log.info("==========================================================");
            log.info(" MMS migration starting | mode={} batchSize={} skipSeeding={}",
                    mode, properties.getBatchSize(), properties.isSkipSeeding());
            log.info("==========================================================");

            MigrationService.RunSummary summary = switch (mode) {
                case "dry-run" -> migrationService.dryRun();
                case "migrate" -> migrationService.migrate();
                case "verify" -> migrationService.verify();
                default -> {
                    log.error("Unknown migration.mode '{}'. Expected one of: dry-run, migrate, verify.", mode);
                    yield null;
                }
            };

            if (summary == null) {
                exitCode = 2;
            } else {
                summary.logFinalTable(log);
                if (summary.hasFailures()) {
                    log.error("Migration finished with {} failure(s). Exit code 1.", summary.totalFailures());
                    exitCode = 1;
                } else {
                    log.info("Migration finished cleanly. Exit code 0.");
                }
            }
        } catch (Throwable t) {
            // Top-level catch: never leak a stack trace back up into Spring's
            // runner machinery — the batch must exit with a clear non-zero code
            // and a single error line.
            log.error("Migration aborted with an unhandled exception. Exit code 1.", t);
            exitCode = 1;
        } finally {
            System.exit(exitCode);
        }
    }
}
