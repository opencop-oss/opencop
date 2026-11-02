// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.reconciliation;

import com.opencop.core.api.general.business.service.DataSourceAssignmentReconciler;
import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.DataSourceStartupCoordinator;
import com.opencop.core.api.general.technical.config.StartupLeader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixed-delay scheduler for Core data-source assignment reconciliation. */
public class DataSourceAssignmentReconciliationScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(DataSourceAssignmentReconciliationScheduler.class);

  private final DataSourceAssignmentReconciler reconciler;
  private final CoreProperties.AssignmentReconciliation properties;
  private final DataSourceStartupCoordinator startupCoordinator;
  private final StartupLeader startupLeader;
  private final AtomicReference<DataSourceStartupCoordinator.AutoStartStatus>
      lastLoggedBlockedAutoStartStatus = new AtomicReference<>();
  private final ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread thread = new Thread(r, "DataSourceAssignmentReconciler");
            thread.setDaemon(true);
            return thread;
          });

  public DataSourceAssignmentReconciliationScheduler(
      DataSourceAssignmentReconciler reconciler,
      CoreProperties.AssignmentReconciliation properties,
      DataSourceStartupCoordinator startupCoordinator,
      StartupLeader startupLeader) {
    this.reconciler = reconciler;
    this.properties = properties;
    this.startupCoordinator = startupCoordinator;
    this.startupLeader = startupLeader;
  }

  @PostConstruct
  public void start() {
    if (!properties.isEnabled()) {
      log.info("Core data-source assignment reconciliation is disabled");
      return;
    }

    if (!startupLeader.isLeader()) {
      log.info("Core data-source assignment reconciliation is disabled on this non-leader Core");
      return;
    }

    var interval = positive(properties.getInterval(), Duration.ofSeconds(15));
    var initialDelay = positiveOrZero(properties.getInitialDelay(), Duration.ofSeconds(15));
    log.info(
        "Starting Core data-source assignment reconciliation every {} after initial delay {}",
        interval,
        initialDelay);
    executor.scheduleWithFixedDelay(
        this::safeReconcile, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  private void safeReconcile() {
    try {
      if (!startupCoordinator.isAutoStartResolved()) {
        logBlockedStartupStatus();
        return;
      }
      lastLoggedBlockedAutoStartStatus.set(null);
      reconciler.reconcileOnce();
    } catch (Exception e) {
      log.warn("Data source assignment reconciliation tick failed: {}", e.getMessage(), e);
    }
  }

  private void logBlockedStartupStatus() {
    var status = startupCoordinator.autoStartStatus();
    if (lastLoggedBlockedAutoStartStatus.getAndSet(status) != status) {
      log.info(
          "Skipping data source assignment reconciliation until startup auto-start resolves"
              + " (status: {})",
          status);
      return;
    }
    log.debug(
        "Skipping data source assignment reconciliation until startup auto-start resolves"
            + " (status: {})",
        status);
  }

  private static Duration positive(Duration value, Duration fallback) {
    if (value == null || value.isZero() || value.isNegative()) {
      return fallback;
    }
    return value;
  }

  private static Duration positiveOrZero(Duration value, Duration fallback) {
    if (value == null || value.isNegative()) {
      return fallback;
    }
    return value;
  }
}
