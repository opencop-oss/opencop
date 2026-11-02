// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public interface HistoricalJobAdmissionCoordinator {
  HistoricalJobAdmissionDecision register(HistoricalTracksJobSubmission job);

  HistoricalJobAdmissionDecision recordExecutionExit(UUID jobId);

  HistoricalJobAdmissionDecision recordTerminal(UUID jobId);

  default HistoricalJobAdmissionDecision cleanupAfterRegistrationFailure(UUID jobId) {
    return recordExecutionExit(jobId);
  }

  default boolean supportsStaleAdmissionRecovery() {
    return false;
  }

  default List<HistoricalJobAdmissionDecision> recoverTerminalActiveJobs(
      HistoricalJobStateGateway jobStateGateway) {
    return recoverTerminalActiveJobs(jobStateGateway, ignored -> false);
  }

  default List<HistoricalJobAdmissionDecision> recoverTerminalActiveJobs(
      HistoricalJobStateGateway jobStateGateway, Predicate<UUID> isLocallyTracked) {
    return List.of();
  }

  default List<HistoricalJobAdmissionDecision> recoverOrphanedActiveJobs(
      HistoricalJobStateGateway jobStateGateway,
      Predicate<UUID> isLocallyTracked,
      BiPredicate<UUID, HistoricalJobAdmissionRecoveryState> recoverer) {
    return List.of();
  }
}
