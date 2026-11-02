// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.runtime;

import com.opencop.core.api.general.ports.technical.gateway.ResourceActivityMaintenance;
import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.StartupLeader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixed-delay scheduler for pruning the activity ledger and orphaned projection rows.
 *
 * <p>Modelled on {@link RuntimeObservationCleanupScheduler}, including its leader gate: several
 * Core instances would otherwise all run the same delete, and the extra work buys nothing.
 *
 * <p>This is the one recurring job the feature keeps. It is not the scan the design set out to
 * remove — that one read every retained observation on a timer to derive current state. This
 * deletes aged rows from an index on {@code effective_at} and never derives anything.
 */
public class ResourceActivityCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(ResourceActivityCleanupScheduler.class);

  private final ResourceActivityMaintenance maintenance;
  private final CoreProperties.ResourceActivityCleanup properties;
  private final StartupLeader startupLeader;
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            var thread = new Thread(r, "ResourceActivityCleanup");
            thread.setDaemon(true);
            return thread;
          });

  public ResourceActivityCleanupScheduler(
      ResourceActivityMaintenance maintenance,
      CoreProperties.ResourceActivityCleanup properties,
      StartupLeader startupLeader) {
    this.maintenance = maintenance;
    this.properties = properties;
    this.startupLeader = startupLeader;
  }

  @PostConstruct
  public void start() {
    if (!properties.isEnabled()) {
      log.info("Resource activity cleanup is disabled");
      return;
    }

    if (!startupLeader.isLeader()) {
      log.info("Resource activity cleanup is disabled on this non-leader Core");
      return;
    }

    var interval = positive(properties.getInterval(), Duration.ofHours(6));
    var initialDelay = positiveOrZero(properties.getInitialDelay(), Duration.ofMinutes(10));
    log.info(
        "Starting resource activity cleanup every {} after initial delay {}",
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
      var retention = positive(properties.getRetention(), Duration.ofDays(7));
      var aged = maintenance.deleteActivityOlderThan(retention);
      var orphaned = maintenance.deleteOrphanedProjections();
      if (aged > 0 || orphaned > 0) {
        log.info("Deleted {} aged activity rows and {} orphaned projection rows", aged, orphaned);
      }
    } catch (Exception e) {
      log.warn("Resource activity cleanup tick failed: {}", e.getMessage(), e);
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  private static Duration positiveOrZero(Duration value, Duration fallback) {
    return value == null || value.isNegative() ? fallback : value;
  }
}
