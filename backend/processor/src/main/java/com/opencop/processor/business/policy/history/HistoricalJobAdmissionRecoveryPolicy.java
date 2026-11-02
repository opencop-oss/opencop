// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionRecoveryState;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalJobAdmissionRecoveryPolicy {

  private static final String CANCELLATION_HEARTBEAT_STOPPED_REASON =
      "Historical job cancelled after processor worker heartbeat stopped";
  private static final String MAX_RUNTIME_EXCEEDED_REASON =
      "Historical job exceeded max runtime after processor recovery";
  private static final String WORKER_HEARTBEAT_STOPPED_REASON =
      "Historical job worker heartbeat stopped after processor restart";

  public HistoricalJobAdmissionRecoveryDecision decide(
      HistoricalJobAdmissionRecoveryState state,
      Instant now,
      Duration heartbeatGrace,
      Duration maxRuntime,
      Duration jobStateTtl) {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(heartbeatGrace, "heartbeatGrace");
    Objects.requireNonNull(maxRuntime, "maxRuntime");
    Objects.requireNonNull(jobStateTtl, "jobStateTtl");

    if (state.terminal()) {
      return HistoricalJobAdmissionRecoveryDecision.recordTerminal();
    }
    if (isCancellationRequestedOrphan(state, now, heartbeatGrace, jobStateTtl)) {
      return HistoricalJobAdmissionRecoveryDecision.markCancelledAndRecordTerminal(
          cancellationReason(state));
    }
    if (exceededMaxRuntime(state, now, maxRuntime)) {
      return HistoricalJobAdmissionRecoveryDecision.markFailedAndRecordTerminal(
          HistoricalJobStage.TIMEOUT, MAX_RUNTIME_EXCEEDED_REASON);
    }
    if (isRunningHeartbeatOrphan(state, now, heartbeatGrace, jobStateTtl)) {
      return HistoricalJobAdmissionRecoveryDecision.markFailedAndRecordTerminal(
          HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED, WORKER_HEARTBEAT_STOPPED_REASON);
    }
    return HistoricalJobAdmissionRecoveryDecision.none();
  }

  private static String cancellationReason(HistoricalJobAdmissionRecoveryState state) {
    String reason = state.failureReason();
    if (reason == null || reason.isBlank()) {
      return CANCELLATION_HEARTBEAT_STOPPED_REASON;
    }
    return reason;
  }

  private static boolean isCancellationRequestedOrphan(
      HistoricalJobAdmissionRecoveryState state,
      Instant now,
      Duration heartbeatGrace,
      Duration jobStateTtl) {
    if (state.stage() != HistoricalJobStage.CANCELLATION_REQUESTED) {
      return false;
    }
    Instant lastHeartbeat = state.expiresAt().minus(jobStateTtl);
    return lastHeartbeat.plus(heartbeatGrace).isBefore(now);
  }

  private static boolean exceededMaxRuntime(
      HistoricalJobAdmissionRecoveryState state, Instant now, Duration maxRuntime) {
    return state.startedAt() != null && state.startedAt().plus(maxRuntime).isBefore(now);
  }

  private static boolean isRunningHeartbeatOrphan(
      HistoricalJobAdmissionRecoveryState state,
      Instant now,
      Duration heartbeatGrace,
      Duration jobStateTtl) {
    HistoricalJobStage stage = state.stage();
    if (stage != HistoricalJobStage.EXECUTION_RUNNING
        && stage != HistoricalJobStage.PLANNING
        && stage != HistoricalJobStage.PLANNED) {
      return false;
    }
    Instant lastHeartbeat = state.expiresAt().minus(jobStateTtl);
    return lastHeartbeat.plus(heartbeatGrace).isBefore(now);
  }
}
