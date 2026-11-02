// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobLifecycleUpdater;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessorHistoricalJobLifecycleUpdater implements HistoricalJobLifecycleUpdater {

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalJobTerminalCoordinator terminalCoordinator;

  public ProcessorHistoricalJobLifecycleUpdater(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalJobTerminalCoordinator terminalCoordinator) {
    this.jobStateGateway = jobStateGateway;
    this.terminalCoordinator = terminalCoordinator;
  }

  @Override
  public boolean markCompleted(UUID jobId, Duration ttl) {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(ttl, "ttl");
    boolean updated = jobStateGateway.markDeliveredCompleted(jobId);
    if (updated) {
      finalizeTerminal(jobId);
    }
    return updated;
  }

  @Override
  public boolean markFailed(UUID jobId, String failureReason, Duration ttl) {
    Objects.requireNonNull(jobId, "jobId");
    Objects.requireNonNull(ttl, "ttl");
    boolean updated = jobStateGateway.markDeliveryFailed(jobId, failureReason);
    if (updated) {
      finalizeTerminal(jobId);
    }
    return updated;
  }

  private void finalizeTerminal(UUID jobId) {
    terminalCoordinator.finalizeIfTerminal(jobId);
  }
}
