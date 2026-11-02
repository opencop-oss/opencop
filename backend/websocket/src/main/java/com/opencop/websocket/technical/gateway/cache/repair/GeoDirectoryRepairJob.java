// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import com.opencop.core.sdk.technical.gateway.cache.redis.RedisGeoDirectoryRepair;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/** One completion-based cron task; the dedicated worker owns each pass and its progress. */
public final class GeoDirectoryRepairJob implements SmartLifecycle, DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(GeoDirectoryRepairJob.class);
  private static final String PREFIX = "opencop_geo_directory_repair_";
  private final RedisGeoDirectoryRepair repair;
  private final GeoDirectoryRepairProperties properties;
  private final WebSocketStartupLeader leader;
  private final TaskScheduler scheduler;
  private final MeterRegistry metrics;
  // Zero means no successful pass in this process; the gauge resets on restart.
  private final AtomicLong lastSuccess = new AtomicLong();
  private ScheduledFuture<?> task;
  private volatile boolean running;

  public GeoDirectoryRepairJob(
      RedisGeoDirectoryRepair repair,
      GeoDirectoryRepairProperties properties,
      WebSocketStartupLeader leader,
      TaskScheduler scheduler,
      MeterRegistry metrics) {
    this.repair = repair;
    this.properties = properties;
    this.leader = leader;
    this.scheduler = scheduler;
    this.metrics = metrics;
    metrics.gauge(PREFIX + "last_success_timestamp_seconds", lastSuccess);
  }

  @Override
  public synchronized void start() {
    if (running) return;
    if ("-".equals(properties.cron())) {
      log.info("Geo directory repair scheduling: status=DISABLED");
      return;
    }
    if (!leader.isLeader()) {
      log.info("Geo directory repair scheduling: status=NOT_LEADER");
      return;
    }
    task =
        scheduler.schedule(
            this::run, new CronTrigger(properties.cron(), ZoneId.of(properties.zone())));
    running = true;
    log.info("Geo directory repair scheduling: status=SCHEDULED");
  }

  private enum Outcome {
    COMPLETE,
    TOPOLOGY_CHANGED,
    FAILED,
    CANCELLED
  }

  void run() {
    var progress = new RedisGeoDirectoryRepair.Progress();
    var start = System.nanoTime();
    var outcome = Outcome.FAILED;
    var failureType = "none";
    log.info("Geo directory repair started");
    try {
      outcome =
          switch (repair.repair(
              properties.scanCount(),
              properties.registrationBatchSize(),
              () -> TimeUnit.NANOSECONDS.sleep(properties.scanPause().toNanos()),
              progress)) {
            case COMPLETE -> Outcome.COMPLETE;
            case TOPOLOGY_CHANGED -> Outcome.TOPOLOGY_CHANGED;
          };
      if (outcome == Outcome.COMPLETE) lastSuccess.set(Instant.now().getEpochSecond());
    } catch (InterruptedException exception) {
      outcome = Outcome.CANCELLED;
      failureType = exception.getClass().getName();
      Thread.currentThread().interrupt();
    } catch (RuntimeException exception) {
      // Redis messages and causes may contain keys, addresses, or values. Log only the type.
      failureType = exception.getClass().getName();
      outcome = Outcome.FAILED;
    } finally {
      var elapsed = System.nanoTime() - start;
      metrics
          .timer(PREFIX + "duration_seconds", "outcome", outcome.name())
          .record(elapsed, TimeUnit.NANOSECONDS);
      metrics.counter(PREFIX + "scan_requests_total").increment(progress.scans());
      metrics.counter(PREFIX + "restored_members_total").increment(progress.restored());
      metrics.counter(PREFIX + "malformed_markers_total").increment(progress.malformed());
      if (outcome == Outcome.COMPLETE) {
        log.info(
            "Geo directory repair complete: scans={}, restored={}, malformed={}, durationMs={}",
            progress.scans(),
            progress.restored(),
            progress.malformed(),
            TimeUnit.NANOSECONDS.toMillis(elapsed));
      } else {
        log.warn(
            "Geo directory repair incomplete: outcome={}, stage={}, failureType={}, scans={}, restored={}, malformed={}, durationMs={}",
            outcome,
            progress.stage(),
            failureType,
            progress.scans(),
            progress.restored(),
            progress.malformed(),
            TimeUnit.NANOSECONDS.toMillis(elapsed));
      }
    }
  }

  @Override
  public synchronized void stop() {
    if (task != null) task.cancel(true);
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Stop before ThreadPoolTaskScheduler (MAX_VALUE / 2) and the Redis factory.
    return Integer.MAX_VALUE;
  }

  @Override
  public void destroy() {
    stop();
  }
}
