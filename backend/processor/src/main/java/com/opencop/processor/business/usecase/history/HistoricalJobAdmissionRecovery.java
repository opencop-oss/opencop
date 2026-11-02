// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionRecoveryState;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoricalJobAdmissionRecovery {

  private static final Logger log = LoggerFactory.getLogger(HistoricalJobAdmissionRecovery.class);

  private final HistoricalJobAdmissionCoordinator admissionCoordinator;
  private final HistoricalJobStateGateway jobStateGateway;
  private final ScheduledExecutorService historicalJobScheduler;
  private final Duration admissionRecoveryInterval;
  private final Duration ttlRefreshInterval;
  private final Duration maxRuntime;
  private final Duration jobStateTtl;
  private final HistoricalJobAdmissionRecoveryPolicy recoveryPolicy;
  private final Predicate<UUID> jobIsTracked;
  private final Consumer<HistoricalJobAdmissionDecision> recoveredDecisionProcessor;
  private final ScheduledFuture<?> recoveryFuture;

  public HistoricalJobAdmissionRecovery(
      HistoricalJobAdmissionCoordinator admissionCoordinator,
      HistoricalJobStateGateway jobStateGateway,
      ScheduledExecutorService historicalJobScheduler,
      Duration admissionRecoveryInterval,
      Duration ttlRefreshInterval,
      Duration maxRuntime,
      Duration jobStateTtl,
      HistoricalJobAdmissionRecoveryPolicy recoveryPolicy,
      Predicate<UUID> jobIsTracked,
      Consumer<HistoricalJobAdmissionDecision> recoveredDecisionProcessor) {
    this.admissionCoordinator = admissionCoordinator;
    this.jobStateGateway = jobStateGateway;
    this.historicalJobScheduler = historicalJobScheduler;
    this.admissionRecoveryInterval = admissionRecoveryInterval;
    this.ttlRefreshInterval = ttlRefreshInterval;
    this.maxRuntime = maxRuntime;
    this.jobStateTtl = jobStateTtl;
    this.recoveryPolicy = recoveryPolicy;
    this.jobIsTracked = jobIsTracked;
    this.recoveredDecisionProcessor = recoveredDecisionProcessor;
    this.recoveryFuture = scheduleAdmissionRecovery();
  }

  public void close() {
    if (recoveryFuture != null) {
      recoveryFuture.cancel(false);
    }
  }

  public void recoverStaleAdmission() {
    if (!admissionCoordinator.supportsStaleAdmissionRecovery()) {
      return;
    }
    var decisions =
        new ArrayList<>(
            admissionCoordinator.recoverTerminalActiveJobs(jobStateGateway, jobIsTracked));
    decisions.addAll(
        admissionCoordinator.recoverOrphanedActiveJobs(
            jobStateGateway, jobIsTracked, this::recoverOrphanedActiveJob));
    for (HistoricalJobAdmissionDecision decision : decisions) {
      recoveredDecisionProcessor.accept(decision);
    }
  }

  private boolean recoverOrphanedActiveJob(UUID jobId, HistoricalJobAdmissionRecoveryState state) {
    var decision =
        recoveryPolicy.decide(
            state, Instant.now(), orphanedExecutionHeartbeatGrace(), maxRuntime, jobStateTtl);
    switch (decision.action()) {
      case NONE:
        return false;
      case RECORD_TERMINAL:
        return true;
      case MARK_CANCELLED_AND_RECORD_TERMINAL:
        return jobStateGateway.markCancelled(jobId, decision.reason());
      case MARK_FAILED_AND_RECORD_TERMINAL:
        return jobStateGateway.markFailed(jobId, decision.failureStage(), decision.reason());
    }
    throw new IllegalStateException(
        "Unsupported historical admission recovery action: " + decision.action());
  }

  private ScheduledFuture<?> scheduleAdmissionRecovery() {
    if (!admissionCoordinator.supportsStaleAdmissionRecovery()) {
      return null;
    }
    long intervalMillis = Math.max(1_000L, admissionRecoveryInterval.toMillis());
    return historicalJobScheduler.scheduleAtFixedRate(
        this::recoverStaleAdmissionSafely, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
  }

  private void recoverStaleAdmissionSafely() {
    try {
      recoverStaleAdmission();
    } catch (RuntimeException e) {
      log.warn("Historical admission recovery failed", e);
    }
  }

  private Duration orphanedExecutionHeartbeatGrace() {
    Duration refreshGrace = ttlRefreshInterval.multipliedBy(2);
    return refreshGrace.compareTo(admissionRecoveryInterval) >= 0
        ? refreshGrace
        : admissionRecoveryInterval;
  }
}
