// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryDecision.Action;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionRecoveryState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoricalJobAdmissionRecoveryPolicyTest {

  private static final Instant LAST_HEARTBEAT = Instant.parse("2026-04-07T12:00:00Z");
  private static final Duration HEARTBEAT_GRACE = Duration.ofMinutes(2);
  private static final Duration MAX_RUNTIME = Duration.ofMinutes(30);
  private static final Duration JOB_STATE_TTL = Duration.ofHours(24);
  private static final Instant STALE_NOW = LAST_HEARTBEAT.plus(HEARTBEAT_GRACE).plusSeconds(1);

  private final HistoricalJobAdmissionRecoveryPolicy policy =
      new HistoricalJobAdmissionRecoveryPolicy();

  @Test
  void decide_whenStateIsTerminal_recordsTerminal() {
    var decision = decide(state(true, HistoricalJobStage.EXECUTION_COMPLETED, null, null));

    assertEquals(Action.RECORD_TERMINAL, decision.action());
  }

  @Test
  void decide_whenCancellationRequestedHeartbeatIsStale_marksCancelledWithExistingReason() {
    var state =
        state(
            false, HistoricalJobStage.CANCELLATION_REQUESTED, null, "replacement request accepted");

    var decision = decide(state);

    assertEquals(Action.MARK_CANCELLED_AND_RECORD_TERMINAL, decision.action());
    assertEquals("replacement request accepted", decision.reason());
  }

  @Test
  void decide_whenCancellationRequestedHeartbeatIsStaleWithoutReason_usesFallbackReason() {
    var decision = decide(state(false, HistoricalJobStage.CANCELLATION_REQUESTED, null, ""));

    assertEquals(Action.MARK_CANCELLED_AND_RECORD_TERMINAL, decision.action());
    assertEquals(
        "Historical job cancelled after processor worker heartbeat stopped", decision.reason());
  }

  @Test
  void decide_whenMaxRuntimeExceeded_marksFailedWithTimeout() {
    var state = state(false, HistoricalJobStage.EXECUTION_RUNNING, LAST_HEARTBEAT, null);
    var now = LAST_HEARTBEAT.plus(MAX_RUNTIME).plusSeconds(1);

    var decision = policy.decide(state, now, HEARTBEAT_GRACE, MAX_RUNTIME, JOB_STATE_TTL);

    assertEquals(Action.MARK_FAILED_AND_RECORD_TERMINAL, decision.action());
    assertEquals(HistoricalJobStage.TIMEOUT, decision.failureStage());
    assertEquals("Historical job exceeded max runtime after processor recovery", decision.reason());
  }

  @Test
  void decide_whenRunningHeartbeatIsStale_marksFailedAsInterrupted() {
    var decision = decide(state(false, HistoricalJobStage.EXECUTION_RUNNING, LAST_HEARTBEAT, null));

    assertEquals(Action.MARK_FAILED_AND_RECORD_TERMINAL, decision.action());
    assertEquals(HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED, decision.failureStage());
    assertEquals(
        "Historical job worker heartbeat stopped after processor restart", decision.reason());
  }

  @Test
  void decide_whenPlanningHeartbeatIsStale_marksFailedAsInterrupted() {
    var decision = decide(state(false, HistoricalJobStage.PLANNING, LAST_HEARTBEAT, null));

    assertEquals(Action.MARK_FAILED_AND_RECORD_TERMINAL, decision.action());
    assertEquals(HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED, decision.failureStage());
  }

  @Test
  void decide_whenStateIsFresh_returnsNoAction() {
    var state = state(false, HistoricalJobStage.EXECUTION_RUNNING, LAST_HEARTBEAT, null);
    var freshNow = LAST_HEARTBEAT.plus(HEARTBEAT_GRACE);

    var decision = policy.decide(state, freshNow, HEARTBEAT_GRACE, MAX_RUNTIME, JOB_STATE_TTL);

    assertEquals(Action.NONE, decision.action());
  }

  private HistoricalJobAdmissionRecoveryDecision decide(HistoricalJobAdmissionRecoveryState state) {
    return policy.decide(state, STALE_NOW, HEARTBEAT_GRACE, MAX_RUNTIME, JOB_STATE_TTL);
  }

  private static HistoricalJobAdmissionRecoveryState state(
      boolean terminal, HistoricalJobStage stage, Instant startedAt, String failureReason) {
    return new HistoricalJobAdmissionRecoveryState(
        terminal, stage, startedAt, LAST_HEARTBEAT.plus(JOB_STATE_TTL), failureReason);
  }
}
