// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.reconciliation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.service.DataSourceAssignmentReconciler;
import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.DataSourceStartupCoordinator;
import com.opencop.core.api.general.technical.config.StartupLeader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DataSourceAssignmentReconciliationSchedulerTest {

  @Test
  void scheduler_waitsForStartupAutoStartResolutionBeforeReconciling() throws Exception {
    var reconciler = mock(DataSourceAssignmentReconciler.class);
    var startupCoordinator = new DataSourceStartupCoordinator();
    var scheduler = scheduler(reconciler, startupCoordinator);

    try {
      scheduler.start();

      Thread.sleep(100);
      verify(reconciler, never()).reconcileOnce();

      startupCoordinator.autoStartCompleted();

      verify(reconciler, timeout(500).atLeastOnce()).reconcileOnce();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_runsWhenStartupAutoStartWasSkipped() {
    var reconciler = mock(DataSourceAssignmentReconciler.class);
    var startupCoordinator = new DataSourceStartupCoordinator();
    startupCoordinator.autoStartSkipped();
    var scheduler = scheduler(reconciler, startupCoordinator);

    try {
      scheduler.start();

      verify(reconciler, timeout(500).atLeastOnce()).reconcileOnce();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_runsWhenStartupAutoStartFailed() {
    var reconciler = mock(DataSourceAssignmentReconciler.class);
    var startupCoordinator = new DataSourceStartupCoordinator();
    startupCoordinator.autoStartFailed();
    var scheduler = scheduler(reconciler, startupCoordinator);

    try {
      scheduler.start();

      verify(reconciler, timeout(500).atLeastOnce()).reconcileOnce();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_doesNotRunOnNonLeaderCore() throws Exception {
    var reconciler = mock(DataSourceAssignmentReconciler.class);
    var startupCoordinator = new DataSourceStartupCoordinator();
    startupCoordinator.autoStartSkipped();
    var startupLeader = mock(StartupLeader.class);
    when(startupLeader.isLeader()).thenReturn(false);
    var scheduler = scheduler(reconciler, startupCoordinator, startupLeader);

    try {
      scheduler.start();

      Thread.sleep(100);
      verify(reconciler, never()).reconcileOnce();
    } finally {
      scheduler.stop();
    }
  }

  private static DataSourceAssignmentReconciliationScheduler scheduler(
      DataSourceAssignmentReconciler reconciler, DataSourceStartupCoordinator startupCoordinator) {
    var startupLeader = mock(StartupLeader.class);
    when(startupLeader.isLeader()).thenReturn(true);
    return scheduler(reconciler, startupCoordinator, startupLeader);
  }

  private static DataSourceAssignmentReconciliationScheduler scheduler(
      DataSourceAssignmentReconciler reconciler,
      DataSourceStartupCoordinator startupCoordinator,
      StartupLeader startupLeader) {
    var properties = new CoreProperties.AssignmentReconciliation();
    properties.setInitialDelay(Duration.ZERO);
    properties.setInterval(Duration.ofMillis(10));
    return new DataSourceAssignmentReconciliationScheduler(
        reconciler, properties, startupCoordinator, startupLeader);
  }
}
