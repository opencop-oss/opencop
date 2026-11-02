// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class HistoricalJobTerminalCoordinator {

  private final HistoricalJobStateGateway jobStateGateway;
  private final HistoricalJobAdmissionCoordinator admissionCoordinator;
  private final Set<Consumer<HistoricalJobTerminalDecision>> listeners =
      ConcurrentHashMap.newKeySet();

  public HistoricalJobTerminalCoordinator(
      HistoricalJobStateGateway jobStateGateway,
      HistoricalJobAdmissionCoordinator admissionCoordinator) {
    this.jobStateGateway = jobStateGateway;
    this.admissionCoordinator = admissionCoordinator;
  }

  public Runnable register(Consumer<HistoricalJobTerminalDecision> listener) {
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  public Optional<HistoricalJobTerminalDecision> finalizeIfTerminal(UUID jobId) {
    var currentState = jobStateGateway.findByJobId(jobId);
    if (currentState.isEmpty() || !currentState.get().isTerminal()) {
      return Optional.empty();
    }
    var decision =
        new HistoricalJobTerminalDecision(
            jobId, currentState.get().requestId(), admissionCoordinator.recordTerminal(jobId));
    for (Consumer<HistoricalJobTerminalDecision> listener : listeners) {
      listener.accept(decision);
    }
    return Optional.of(decision);
  }
}
