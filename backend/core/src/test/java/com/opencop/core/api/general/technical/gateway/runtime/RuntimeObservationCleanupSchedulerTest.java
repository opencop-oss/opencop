// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationMaintenance;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RuntimeObservationCleanupSchedulerTest {

  @Test
  void scheduler_doesNotRunWhenDisabled() throws Exception {
    var maintenance = mock(SourceRuntimeObservationMaintenance.class);
    var startupLeader = leader(true);
    var properties = properties();
    properties.setEnabled(false);
    var scheduler = new RuntimeObservationCleanupScheduler(maintenance, properties, startupLeader);

    try {
      scheduler.start();

      Thread.sleep(100);
      verify(maintenance, never()).deleteExpiredOlderThan(any());
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_doesNotRunOnNonLeaderCore() throws Exception {
    var maintenance = mock(SourceRuntimeObservationMaintenance.class);
    var scheduler =
        new RuntimeObservationCleanupScheduler(maintenance, properties(), leader(false));

    try {
      scheduler.start();

      Thread.sleep(100);
      verify(maintenance, never()).deleteExpiredOlderThan(any());
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_runsCleanupOnLeaderCore() {
    var maintenance = mock(SourceRuntimeObservationMaintenance.class);
    var scheduler = new RuntimeObservationCleanupScheduler(maintenance, properties(), leader(true));

    try {
      scheduler.start();

      verify(maintenance, timeout(500).atLeastOnce()).deleteExpiredOlderThan(Duration.ofHours(24));
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void scheduler_continuesAfterCleanupFailure() {
    var maintenance = mock(SourceRuntimeObservationMaintenance.class);
    doThrow(new IllegalStateException("postgres unavailable"))
        .doReturn(0)
        .when(maintenance)
        .deleteExpiredOlderThan(Duration.ofHours(24));
    var scheduler = new RuntimeObservationCleanupScheduler(maintenance, properties(), leader(true));

    try {
      scheduler.start();

      verify(maintenance, timeout(500).atLeast(2)).deleteExpiredOlderThan(Duration.ofHours(24));
    } finally {
      scheduler.stop();
    }
  }

  private static CoreProperties.RuntimeObservationCleanup properties() {
    var properties = new CoreProperties.RuntimeObservationCleanup();
    properties.setInitialDelay(Duration.ZERO);
    properties.setInterval(Duration.ofMillis(10));
    properties.setRetention(Duration.ofHours(24));
    return properties;
  }

  private static StartupLeader leader(boolean isLeader) {
    var startupLeader = mock(StartupLeader.class);
    when(startupLeader.isLeader()).thenReturn(isLeader);
    return startupLeader;
  }
}
