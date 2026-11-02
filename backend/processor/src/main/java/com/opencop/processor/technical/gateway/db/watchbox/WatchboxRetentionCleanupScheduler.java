// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.ProcessorStartupLeader;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leader-elected, age-based batched retention cleanup for a watchbox runtime table. The watchbox
 * tables have no partition drops, so a bounded batched delete bounds their growth; the tick bound
 * keeps a large backlog from monopolizing the connection, and the bound-exhausted counter is the
 * backlog alarm. One instance per table, wired in {@code WatchboxStateConfig}.
 */
public class WatchboxRetentionCleanupScheduler {

  /** Deletes rows aged past {@code cutoff}, at most {@code batchSize}; returns rows deleted. */
  @FunctionalInterface
  public interface ExpiredBatchDelete {
    int delete(Instant cutoff, int batchSize);
  }

  /** Static snapshot of the owning properties object's GC shape. */
  public record Settings(
      boolean enabled,
      Duration interval,
      Duration initialDelay,
      int batchSize,
      int maxBatchesPerTick,
      Duration retention) {}

  private static final Logger log =
      LoggerFactory.getLogger(WatchboxRetentionCleanupScheduler.class);

  private final String subject;
  private final Settings settings;
  private final ExpiredBatchDelete delete;
  // Optional in-memory companion prune (the transition-state arm's churn-breaker scopes);
  // null means the table has no in-memory companion.
  private final ToIntFunction<Instant> postSweep;
  private final String metricPrefix;
  private final ProcessorStartupLeader startupLeader;
  private final Clock clock;
  private final ProcessorMetrics metrics;
  private final ScheduledExecutorService executor;

  public WatchboxRetentionCleanupScheduler(
      String subject,
      Settings settings,
      ExpiredBatchDelete delete,
      String metricPrefix,
      ProcessorStartupLeader startupLeader,
      Clock clock,
      ProcessorMetrics metrics) {
    this(subject, settings, delete, null, metricPrefix, startupLeader, clock, metrics);
  }

  public WatchboxRetentionCleanupScheduler(
      String subject,
      Settings settings,
      ExpiredBatchDelete delete,
      ToIntFunction<Instant> postSweep,
      String metricPrefix,
      ProcessorStartupLeader startupLeader,
      Clock clock,
      ProcessorMetrics metrics) {
    this.subject = subject;
    this.settings = settings;
    this.delete = delete;
    this.postSweep = postSweep;
    this.metricPrefix = metricPrefix;
    this.startupLeader = startupLeader;
    this.clock = clock;
    this.metrics = metrics;
    this.executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              var thread = new Thread(runnable, "WatchboxCleanup-" + subject);
              thread.setDaemon(true);
              return thread;
            });
  }

  @PostConstruct
  public void start() {
    if (!settings.enabled()) {
      log.info("Watchbox {} cleanup is disabled", subject);
      return;
    }
    if (!startupLeader.isLeader()) {
      log.info("Watchbox {} cleanup is disabled on this non-leader Processor", subject);
      return;
    }
    log.info(
        "Starting watchbox {} cleanup every {} after initial delay {}",
        subject,
        settings.interval(),
        settings.initialDelay());
    executor.scheduleWithFixedDelay(
        this::safeTick,
        settings.initialDelay().toMillis(),
        settings.interval().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  void tick() {
    if (!settings.enabled() || !startupLeader.isLeader()) {
      return;
    }
    var startedAt = clock.instant();
    var cutoff = startedAt.minus(settings.retention());
    var deletedRows = 0;
    var boundExhausted = false;
    for (int batch = 0; batch < settings.maxBatchesPerTick(); batch++) {
      var deleted = delete.delete(cutoff, settings.batchSize());
      deletedRows += deleted;
      if (deleted < settings.batchSize()) {
        break;
      }
      boundExhausted = batch + 1 == settings.maxBatchesPerTick();
    }
    var pruned = postSweep == null ? 0 : postSweep.applyAsInt(cutoff);
    if (postSweep != null && (deletedRows > 0 || pruned > 0)) {
      log.info(
          "Watchbox {} cleanup deleted {} rows and pruned {} idle scopes",
          subject,
          deletedRows,
          pruned);
    } else if (deletedRows > 0) {
      log.info("Watchbox {} cleanup deleted {} rows", subject, deletedRows);
    }
    if (deletedRows > 0) {
      metrics.incrementCounter(metricPrefix + ".deleted_rows", Tags.empty(), deletedRows);
    }
    if (boundExhausted) {
      // A burst of these right after a fleet reset (watchbox feature re-enable /
      // bumpAllActiveConfigEpochs) is the expected drain of the stranded generation, not organic
      // backlog: the reset freezes the prior generation's timestamps together, so one retention
      // window later the whole generation becomes eligible in a single wave. That wave clears in
      // roughly (generation rows) / (batchSize x maxBatchesPerTick) ticks; a burst outliving that
      // horizon is a real backlog signal.
      metrics.incrementCounter(metricPrefix + ".bound_exhausted", Tags.empty());
      log.warn(
          "Watchbox {} cleanup exhausted its tick bound after deleting {} rows ({} batches of {})",
          subject,
          deletedRows,
          settings.maxBatchesPerTick(),
          settings.batchSize());
    }
    metrics.recordTimer(
        metricPrefix + ".tick.duration",
        Tags.empty(),
        Duration.between(startedAt, clock.instant()));
  }

  private void safeTick() {
    try {
      tick();
    } catch (Exception exception) {
      log.warn("Watchbox {} cleanup tick failed: {}", subject, exception.getMessage(), exception);
    }
  }
}
