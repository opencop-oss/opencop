// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.reconciliation;

import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler;
import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler.ReconciliationMode;
import com.opencop.api.connect.technical.config.ConnectProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixed-delay scheduler for Connect desired-ownership reconciliation. */
public class DesiredOwnershipReconciliationScheduler {

  private static final Logger log =
      LoggerFactory.getLogger(DesiredOwnershipReconciliationScheduler.class);
  private static final Duration DEFAULT_RECONCILIATION_INTERVAL = Duration.ofSeconds(10);
  private static final Duration DEFAULT_RECONCILIATION_INITIAL_DELAY = Duration.ZERO;
  private static final Duration DEFAULT_FULL_RESTART_INTERVAL = Duration.ofHours(12);

  private final DesiredOwnershipReconciler reconciler;
  private final ConnectProperties.Reconciliation reconciliationProperties;
  private final ConnectProperties.PeriodicDataSourceRestart periodicRestartProperties;
  private final ScheduledExecutorService executor;
  private final Clock clock;
  private final Duration fullRestartInterval;
  private Instant nextFullRestartDueAt;

  public DesiredOwnershipReconciliationScheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliationProperties,
      ConnectProperties.PeriodicDataSourceRestart periodicRestartProperties) {
    this(
        reconciler,
        reconciliationProperties,
        periodicRestartProperties,
        newExecutor(),
        Clock.systemUTC());
  }

  DesiredOwnershipReconciliationScheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliationProperties,
      ConnectProperties.PeriodicDataSourceRestart periodicRestartProperties,
      ScheduledExecutorService executor,
      Clock clock) {
    this.reconciler = reconciler;
    this.reconciliationProperties = reconciliationProperties;
    this.periodicRestartProperties = periodicRestartProperties;
    this.executor = executor;
    this.clock = clock;
    this.fullRestartInterval =
        positiveMillis(periodicRestartProperties.getInterval(), DEFAULT_FULL_RESTART_INTERVAL);
    var fullRestartInitialDelay =
        positiveOrZero(periodicRestartProperties.getInitialDelay(), fullRestartInterval);
    nextFullRestartDueAt = clock.instant().plus(fullRestartInitialDelay);
  }

  @PostConstruct
  public void start() {
    if (!reconciliationProperties.isEnabled()) {
      log.info("Connect desired-ownership reconciliation is disabled");
      return;
    }

    var interval =
        positive(reconciliationProperties.getInterval(), DEFAULT_RECONCILIATION_INTERVAL);
    var initialDelay =
        positiveOrZero(
            reconciliationProperties.getInitialDelay(), DEFAULT_RECONCILIATION_INITIAL_DELAY);
    log.info(
        "Starting Connect desired-ownership reconciliation every {} after initial delay {}; periodic full restart enabled={}, interval={}, nextDueAt={}",
        interval,
        initialDelay,
        periodicRestartProperties.isEnabled(),
        fullRestartInterval,
        nextFullRestartDueAt);
    executor.scheduleWithFixedDelay(
        this::safeReconcile, initialDelay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void stop() {
    executor.shutdownNow();
  }

  void safeReconcile() {
    var now = clock.instant();
    var fullDue = periodicRestartProperties.isEnabled() && !now.isBefore(nextFullRestartDueAt);

    try {
      var result =
          fullDue ? reconciler.reconcileOnce(ReconciliationMode.FULL) : reconciler.reconcileOnce();
      if (fullDue && !result.skipped()) {
        advanceNextFullRestartDueAt(now);
      }
    } catch (Exception e) {
      log.warn("Desired ownership reconciliation tick failed: {}", e.getMessage(), e);
    }
  }

  private void advanceNextFullRestartDueAt(Instant now) {
    do {
      nextFullRestartDueAt = nextFullRestartDueAt.plus(fullRestartInterval);
    } while (!nextFullRestartDueAt.isAfter(now));
  }

  private static ScheduledExecutorService newExecutor() {
    return Executors.newSingleThreadScheduledExecutor(
        r -> {
          var thread = new Thread(r, "DesiredOwnershipReconciler");
          thread.setDaemon(true);
          return thread;
        });
  }

  private static Duration positive(Duration value, Duration fallback) {
    if (value == null || value.isZero() || value.isNegative()) {
      return fallback;
    }
    return value;
  }

  private static Duration positiveMillis(Duration value, Duration fallback) {
    if (value == null || value.isZero() || value.isNegative()) {
      return fallback;
    }
    if (value.toMillis() <= 0) {
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
