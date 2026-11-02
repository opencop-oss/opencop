// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.reconciliation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler;
import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler.ReconciliationMode;
import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler.ReconciliationResult;
import com.opencop.api.connect.technical.config.ConnectProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DesiredOwnershipReconciliationSchedulerTest {

  @Test
  void reconciliationDisabled_doesNotSchedule() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    var reconciliation = reconciliationProperties(Duration.ofSeconds(10), Duration.ZERO);
    reconciliation.setEnabled(false);
    var executor = mock(ScheduledExecutorService.class);

    scheduler(
            reconciler, reconciliation, new ConnectProperties.PeriodicDataSourceRestart(), executor)
        .start();

    verify(executor, never())
        .scheduleWithFixedDelay(
            any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void start_usesReconciliationFixedDelayNotPeriodicDelay() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    var reconciliation = reconciliationProperties(Duration.ofSeconds(5), Duration.ofSeconds(2));
    var periodic = periodicProperties(true, Duration.ofHours(1), Duration.ofMinutes(30));
    var executor = mock(ScheduledExecutorService.class);

    scheduler(reconciler, reconciliation, periodic, executor).start();

    verify(executor)
        .scheduleWithFixedDelay(
            any(Runnable.class),
            eq(Duration.ofSeconds(2).toMillis()),
            eq(Duration.ofSeconds(5).toMillis()),
            eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void beforeFullDue_callsNormalMode() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce()).thenReturn(result());
    var periodic = periodicProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(1));
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce();
    verify(reconciler, never()).reconcileOnce(ReconciliationMode.FULL);
  }

  @Test
  void atFullDue_callsFullMode() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(result());
    var clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
    var periodic = periodicProperties(true, Duration.ofMinutes(10), Duration.ofMinutes(1));
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic, clock);

    clock.advance(Duration.ofMinutes(1));
    scheduler.safeReconcile();

    verify(reconciler, never()).reconcileOnce();
    verify(reconciler).reconcileOnce(ReconciliationMode.FULL);
  }

  @Test
  void fullModeSkipped_doesNotAdvanceDueTimeAndRetriesNextTick() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(skipped(), result());
    var periodic = periodicProperties(true, Duration.ofMinutes(10), Duration.ZERO);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    scheduler.safeReconcile();
    scheduler.safeReconcile();

    verify(reconciler, times(2)).reconcileOnce(ReconciliationMode.FULL);
    verify(reconciler, never()).reconcileOnce();
  }

  @Test
  void fullModeNonSkipped_advancesDueTimeAndNextImmediateTickIsNormal() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(result());
    when(reconciler.reconcileOnce()).thenReturn(result());
    var periodic = periodicProperties(true, Duration.ofMinutes(10), Duration.ZERO);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    scheduler.safeReconcile();
    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce(ReconciliationMode.FULL);
    verify(reconciler).reconcileOnce();
  }

  @Test
  void periodicRestartDisabled_stillRunsNormalReconciliation() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce()).thenReturn(result());
    var periodic = periodicProperties(false, Duration.ofMinutes(10), Duration.ZERO);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce();
    verify(reconciler, never()).reconcileOnce(ReconciliationMode.FULL);
  }

  @Test
  void zeroPeriodicInitialDelay_firstTickCanRunFullMode() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(result());
    var periodic = periodicProperties(true, Duration.ofMinutes(10), Duration.ZERO);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce(ReconciliationMode.FULL);
    verify(reconciler, never()).reconcileOnce();
  }

  @Test
  void invalidPeriodicIntervalAndNullInitialDelay_useTwelveHourFallbacks() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce()).thenReturn(result());
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(result());
    var clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
    var periodic = periodicProperties(true, Duration.ZERO, null);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic, clock);

    scheduler.safeReconcile();
    clock.advance(Duration.ofHours(12));
    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce();
    verify(reconciler).reconcileOnce(ReconciliationMode.FULL);
  }

  @Test
  void negativePeriodicInitialDelay_usesResolvedPeriodicInterval() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    when(reconciler.reconcileOnce()).thenReturn(result());
    when(reconciler.reconcileOnce(ReconciliationMode.FULL)).thenReturn(result());
    var clock = new MutableClock(Instant.parse("2026-05-08T00:00:00Z"));
    var periodic = periodicProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(-1));
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic, clock);

    scheduler.safeReconcile();
    clock.advance(Duration.ofMinutes(5));
    scheduler.safeReconcile();

    verify(reconciler).reconcileOnce();
    verify(reconciler).reconcileOnce(ReconciliationMode.FULL);
  }

  @Test
  void tickExceptionDoesNotEscapeAndFutureTicksStillRun() {
    var reconciler = mock(DesiredOwnershipReconciler.class);
    doThrow(new IllegalStateException("first tick failed"))
        .doReturn(result())
        .when(reconciler)
        .reconcileOnce();
    var periodic = periodicProperties(false, Duration.ofMinutes(10), Duration.ZERO);
    var scheduler = scheduler(reconciler, new ConnectProperties.Reconciliation(), periodic);

    assertDoesNotThrow(scheduler::safeReconcile);
    assertDoesNotThrow(scheduler::safeReconcile);

    verify(reconciler, times(2)).reconcileOnce();
  }

  private static DesiredOwnershipReconciliationScheduler scheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliation,
      ConnectProperties.PeriodicDataSourceRestart periodic) {
    return scheduler(
        reconciler,
        reconciliation,
        periodic,
        new MutableClock(Instant.parse("2026-05-08T00:00:00Z")));
  }

  private static DesiredOwnershipReconciliationScheduler scheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliation,
      ConnectProperties.PeriodicDataSourceRestart periodic,
      Clock clock) {
    return scheduler(
        reconciler, reconciliation, periodic, mock(ScheduledExecutorService.class), clock);
  }

  private static DesiredOwnershipReconciliationScheduler scheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliation,
      ConnectProperties.PeriodicDataSourceRestart periodic,
      ScheduledExecutorService executor) {
    return scheduler(
        reconciler,
        reconciliation,
        periodic,
        executor,
        new MutableClock(Instant.parse("2026-05-08T00:00:00Z")));
  }

  private static DesiredOwnershipReconciliationScheduler scheduler(
      DesiredOwnershipReconciler reconciler,
      ConnectProperties.Reconciliation reconciliation,
      ConnectProperties.PeriodicDataSourceRestart periodic,
      ScheduledExecutorService executor,
      Clock clock) {
    return new DesiredOwnershipReconciliationScheduler(
        reconciler, reconciliation, periodic, executor, clock);
  }

  private static ConnectProperties.Reconciliation reconciliationProperties(
      Duration interval, Duration initialDelay) {
    var properties = new ConnectProperties.Reconciliation();
    properties.setInterval(interval);
    properties.setInitialDelay(initialDelay);
    return properties;
  }

  private static ConnectProperties.PeriodicDataSourceRestart periodicProperties(
      boolean enabled, Duration interval, Duration initialDelay) {
    var properties = new ConnectProperties.PeriodicDataSourceRestart();
    properties.setEnabled(enabled);
    properties.setInterval(interval);
    properties.setInitialDelay(initialDelay);
    return properties;
  }

  private static ReconciliationResult result() {
    return new ReconciliationResult(0, 0, 0, 0, false, false);
  }

  private static ReconciliationResult skipped() {
    return new ReconciliationResult(0, 0, 0, 0, true, false);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
