// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionHeartbeatWriter;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.technical.gateway.api.PostgresWatchboxDetectionHeartbeatRepository;
import com.opencop.processor.business.policy.watchbox.WatchboxChurnBreaker;
import com.opencop.processor.business.usecase.watchbox.WatchboxTransitionEngine;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxAlertOutboxRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxDetectionRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxSourceStatusRepository;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxAlertOutboxRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxDetectionRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxSourceStatusRepository;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxTransitionStateRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxAlertOutboxDrainer;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxRetentionCleanupScheduler;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.ProcessorStartupLeader;
import com.opencop.processor.technical.spring.config.ProcessorStartupLeaderProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableConfigurationProperties({
  WatchboxStateProperties.class,
  WatchboxDetectionProperties.class,
  WatchboxSourceStatusProperties.class,
  WatchboxPipelineProperties.class,
  WatchboxAlertProperties.class,
  ProcessorStartupLeaderProperties.class
})
public class WatchboxStateConfig {

  @Bean
  WatchboxTransitionStateRepository watchboxTransitionStateRepository(JdbcTemplate jdbc) {
    return new PostgresWatchboxTransitionStateRepository(jdbc);
  }

  @Bean
  WatchboxSourceStatusRepository watchboxSourceStatusRepository(JdbcTemplate jdbc) {
    return new PostgresWatchboxSourceStatusRepository(jdbc);
  }

  @Bean
  WatchboxChurnBreaker watchboxChurnBreaker(
      WatchboxStateProperties properties, ProcessorMetrics metrics) {
    return new WatchboxChurnBreaker(
        properties.getBreaker().isEnabled(),
        properties.getBreaker().getCap(),
        properties.getRetention(),
        metrics);
  }

  @Bean
  WatchboxDetectionRepository watchboxDetectionRepository(JdbcTemplate jdbc) {
    return new PostgresWatchboxDetectionRepository(jdbc);
  }

  @Bean
  WatchboxAlertOutboxRepository watchboxAlertOutboxRepository(JdbcTemplate jdbc) {
    return new PostgresWatchboxAlertOutboxRepository(jdbc);
  }

  @Bean
  WatchboxAlertOutboxDrainer watchboxAlertOutboxDrainer(
      WatchboxAlertOutboxRepository outboxRepository,
      CachePublisher<WatchboxAlertEvent> watchboxAlertsPublisher,
      PlatformTransactionManager transactionManager,
      WatchboxAlertProperties alertProperties,
      WatchboxStateProperties stateProperties,
      Clock clock,
      ProcessorMetrics metrics) {
    var drain = alertProperties.getDrain();
    return new WatchboxAlertOutboxDrainer(
        outboxRepository,
        watchboxAlertsPublisher,
        new TransactionTemplate(transactionManager),
        // The delivery freshness bound deliberately reuses the ingest live window: an event too
        // old to admit at the pipeline door is also too old to deliver at the exit.
        new WatchboxAlertOutboxDrainer.Settings(
            drain.getBatchSize(), drain.getPollInterval(), stateProperties.getLiveWindow()),
        clock,
        metrics);
  }

  @Bean
  WatchboxDetectionHeartbeatWriter watchboxDetectionHeartbeatWriter(
      NamedParameterJdbcTemplate jdbc) {
    return new PostgresWatchboxDetectionHeartbeatRepository(jdbc);
  }

  @Bean
  WatchboxTransitionEngine watchboxTransitionEngine(
      WatchboxTransitionStateRepository stateRepository,
      WatchboxSourceStatusRepository statusRepository,
      WatchboxDetectionRepository detectionRepository,
      WatchboxAlertOutboxRepository outboxRepository,
      WatchboxChurnBreaker breaker,
      PlatformTransactionManager transactionManager,
      Clock clock,
      WatchboxStateProperties properties,
      ProcessorMetrics metrics) {
    return new WatchboxTransitionEngine(
        stateRepository,
        statusRepository,
        detectionRepository,
        outboxRepository,
        breaker,
        new TransactionTemplate(transactionManager),
        clock,
        properties.getRetention(),
        metrics);
  }

  @Bean
  ProcessorStartupLeader processorStartupLeader(
      StringRedisTemplate redisTemplate, ProcessorStartupLeaderProperties properties) {
    return new ProcessorStartupLeader(
        redisTemplate, properties.getLockKey(), properties.isEnabled());
  }

  @Bean
  WatchboxRetentionCleanupScheduler watchboxTransitionStateCleanupScheduler(
      WatchboxTransitionStateRepository stateRepository,
      WatchboxChurnBreaker breaker,
      WatchboxStateProperties properties,
      ProcessorStartupLeader startupLeader,
      Clock clock,
      ProcessorMetrics metrics) {
    var gc = properties.getGc();
    return new WatchboxRetentionCleanupScheduler(
        "transition-state",
        new WatchboxRetentionCleanupScheduler.Settings(
            gc.isEnabled(),
            gc.getInterval(),
            gc.getInitialDelay(),
            gc.getBatchSize(),
            gc.getMaxBatchesPerTick(),
            properties.getRetention()),
        stateRepository::deleteExpiredBatch,
        breaker::pruneIdleScopes,
        "opencop.watchbox.state.gc",
        startupLeader,
        clock,
        metrics);
  }

  @Bean
  WatchboxRetentionCleanupScheduler watchboxDetectionCleanupScheduler(
      WatchboxDetectionRepository detectionRepository,
      WatchboxDetectionProperties properties,
      ProcessorStartupLeader startupLeader,
      Clock clock,
      ProcessorMetrics metrics) {
    var gc = properties.getGc();
    return new WatchboxRetentionCleanupScheduler(
        "detection",
        new WatchboxRetentionCleanupScheduler.Settings(
            gc.isEnabled(),
            gc.getInterval(),
            gc.getInitialDelay(),
            gc.getBatchSize(),
            gc.getMaxBatchesPerTick(),
            properties.getRetention()),
        detectionRepository::deleteExpiredBatch,
        "opencop.watchbox.detection.gc",
        startupLeader,
        clock,
        metrics);
  }

  @Bean
  WatchboxRetentionCleanupScheduler watchboxSourceStatusCleanupScheduler(
      WatchboxSourceStatusRepository statusRepository,
      WatchboxSourceStatusProperties properties,
      ProcessorStartupLeader startupLeader,
      Clock clock,
      ProcessorMetrics metrics) {
    var gc = properties.getGc();
    return new WatchboxRetentionCleanupScheduler(
        "source-status",
        new WatchboxRetentionCleanupScheduler.Settings(
            gc.isEnabled(),
            gc.getInterval(),
            gc.getInitialDelay(),
            gc.getBatchSize(),
            gc.getMaxBatchesPerTick(),
            gc.getRetention()),
        statusRepository::deleteSupersededBatch,
        "opencop.watchbox.source_status.gc",
        startupLeader,
        clock,
        metrics);
  }
}
