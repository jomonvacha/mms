package com.roots.mms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate} / {@code @LastModifiedDate} support on JPA
 * entities via {@code AuditingEntityListener}. Gated off in the migration
 * profile so the Mongo → Postgres copier can preserve the original audit
 * timestamps instead of overwriting them on re-runs.
 */
@Configuration
@EnableJpaAuditing
@Profile("!migration")
public class JpaAuditingConfig {
}
