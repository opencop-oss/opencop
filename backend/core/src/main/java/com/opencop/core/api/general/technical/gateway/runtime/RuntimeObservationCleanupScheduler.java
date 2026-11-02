// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.runtime;

import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationMaintenance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixed-delay scheduler for pruning expired source runtime observations. */
public class RuntimeObservationCleanupScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(RuntimeObservationCleanupScheduler.class);

  private final SourceRuntimeObservationMaintenance maintenance;
  private final CoreProperties.RuntimeObservationCleanup properties;
  private final StartupLeader startupLeader;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "RuntimeObservationCleanup");
            thread.setDaemon(true);
            return thread;
          });

  public RuntimeObservationCleanupScheduler(
      SourceRuntimeObservationMaintenance maintenance,
      CoreProperties.RuntimeObservationCleanup properties,
      StartupLeader startupLeader) {
    this.maintenance = maintenance;
    this.properties = properties;
    this.startupLeader = startupLeader;
  }

  @PostConstruct
  public void start() {
    if (!properties.isEnabled()) {
      log.info("Runtime observation cleanup is disabled");
      return;
    }

    if (!startupLeader.isLeader()) {
      log.info("Runtime observation cleanup is disabled on this non-leader Core");
      return;
    }

    var interval = positive(properties.getInterval(), Duration.ofHours(1));
    var initialDelay = positiveOrZero(properties.getInitialDelay(), Duration.ofMinutes(5));
    log.info(
        "Starting runtime observation cleanup every {} after initial delay {}",
        interval,
        initialDelay);
    executor.scheduleWithFixedDelay(
        this::safeCleanup, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  private void safeCleanup() {
    try {
      var retention = positive(properties.getRetention(), Duration.ofHours(24));
      var deleted = maintenance.deleteExpiredOlderThan(retention);
      if (deleted > 0) {
        log.info("Deleted {} expired source runtime observation rows", deleted);
      }
    } catch (Exception e) {
      log.warn("Runtime observation cleanup tick failed: {}", e.getMessage(), e);
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static Duration positiveOrZero(Duration value, Duration fallback) {
    return value == null || value.isNegative() ? fallback : value;
  }
}
