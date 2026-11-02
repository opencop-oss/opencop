// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.runtime;

import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthMaintenance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixed-delay scheduler for pruning old Connect worker run rows. */
public class ConnectWorkerRunCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(ConnectWorkerRunCleanupScheduler.class);

  private final ConnectWorkerHealthMaintenance maintenance;
  private final CoreProperties.ConnectWorkerRunCleanup properties;
  private final StartupLeader startupLeader;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "ConnectWorkerRunCleanup");
            thread.setDaemon(true);
            return thread;
          });

  public ConnectWorkerRunCleanupScheduler(
      ConnectWorkerHealthMaintenance maintenance,
      CoreProperties.ConnectWorkerRunCleanup properties,
      StartupLeader startupLeader) {
    this.maintenance = maintenance;
    this.properties = properties;
    this.startupLeader = startupLeader;
  }

  @PostConstruct
  public void start() {
    if (!properties.isEnabled()) {
      log.info("Connect worker run cleanup is disabled");
      return;
    }

    if (!startupLeader.isLeader()) {
      log.info("Connect worker run cleanup is disabled on this non-leader Core");
      return;
    }

    var interval = positive(properties.getInterval(), Duration.ofHours(1));
    var initialDelay = positiveOrZero(properties.getInitialDelay(), Duration.ofMinutes(5));
    log.info(
        "Starting Connect worker run cleanup every {} after initial delay {}",
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
      var retention = positive(properties.getRetention(), Duration.ofDays(30));
      var deleted = maintenance.deleteStoppedOrExpiredOlderThan(retention);
      if (deleted > 0) {
        log.info("Deleted {} old Connect worker run rows", deleted);
      }
    } catch (Exception e) {
      log.warn("Connect worker run cleanup tick failed: {}", e.getMessage(), e);
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static Duration positiveOrZero(Duration value, Duration fallback) {
    return value == null || value.isNegative() ? fallback : value;
  }
}
