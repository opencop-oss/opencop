// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class HistoricalJobAdmissionRecoveryFactory {

  private final Duration admissionRecoveryInterval;
  private final Duration ttlRefreshInterval;
  private final Duration maxRuntime;
  private final Duration jobStateTtl;
  private final HistoricalJobAdmissionRecoveryPolicy recoveryPolicy;

  public HistoricalJobAdmissionRecoveryFactory(
      Duration admissionRecoveryInterval,
      Duration ttlRefreshInterval,
      Duration maxRuntime,
      Duration jobStateTtl,
      HistoricalJobAdmissionRecoveryPolicy recoveryPolicy) {
    this.admissionRecoveryInterval = admissionRecoveryInterval;
    this.ttlRefreshInterval = ttlRefreshInterval;
    this.maxRuntime = maxRuntime;
    this.jobStateTtl = jobStateTtl;
    this.recoveryPolicy = recoveryPolicy;
  }

  public HistoricalJobAdmissionRecovery create(
      HistoricalJobAdmissionCoordinator admissionCoordinator,
      HistoricalJobStateGateway jobStateGateway,
      ScheduledExecutorService historicalJobScheduler,
      Predicate<UUID> jobIsTracked,
      Consumer<HistoricalJobAdmissionDecision> recoveredDecisionProcessor) {
    return new HistoricalJobAdmissionRecovery(
        admissionCoordinator,
        jobStateGateway,
        historicalJobScheduler,
        admissionRecoveryInterval,
        ttlRefreshInterval,
        maxRuntime,
        jobStateTtl,
        recoveryPolicy,
        jobIsTracked,
        recoveredDecisionProcessor);
  }
}
