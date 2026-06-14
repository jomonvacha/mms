package com.roots.mms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled beans at normal runtime. Deliberately excluded from the
 * {@code migration} profile so the data-copy tool runs as a one-shot batch
 * without background jobs firing.
 */
@Configuration
@Profile("!migration")
@EnableScheduling
public class SchedulingConfig {}
