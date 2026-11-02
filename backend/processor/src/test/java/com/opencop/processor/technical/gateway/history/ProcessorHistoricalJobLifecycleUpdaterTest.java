// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessorHistoricalJobLifecycleUpdaterTest {

  @Test
  void markCompleted_finalizesTerminalAdmission() {
    var jobStateGateway = mock(HistoricalJobStateGateway.class);
    var terminalCoordinator = mock(HistoricalJobTerminalCoordinator.class);
    var updater = new ProcessorHistoricalJobLifecycleUpdater(jobStateGateway, terminalCoordinator);
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");
    when(jobStateGateway.markDeliveredCompleted(jobId)).thenReturn(true);

    updater.markCompleted(jobId, Duration.ofHours(1));

    verify(terminalCoordinator).finalizeIfTerminal(jobId);
  }

  @Test
  void markFailed_finalizesTerminalAdmission() {
    var jobStateGateway = mock(HistoricalJobStateGateway.class);
    var terminalCoordinator = mock(HistoricalJobTerminalCoordinator.class);
    var updater = new ProcessorHistoricalJobLifecycleUpdater(jobStateGateway, terminalCoordinator);
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440778");
    when(jobStateGateway.markDeliveryFailed(jobId, "failed")).thenReturn(true);

    updater.markFailed(jobId, "failed", Duration.ofHours(1));

    verify(terminalCoordinator).finalizeIfTerminal(jobId);
  }
}
