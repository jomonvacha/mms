package com.roots.mms.migration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires {@link MigrationProperties} into the Spring context under the
 * {@code migration} profile only. Keeping the binding localised here means
 * the migration properties class is unknown to the default runtime profile,
 * so a stray {@code migration.*} property in application config will not
 * accidentally start the migration app.
 */
@Configuration
@Profile("migration")
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationConfig {
}
