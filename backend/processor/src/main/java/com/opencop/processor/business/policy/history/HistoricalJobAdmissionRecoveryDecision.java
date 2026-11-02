// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;

public record HistoricalJobAdmissionRecoveryDecision(
    Action action, HistoricalJobStage failureStage, String reason) {

  public static HistoricalJobAdmissionRecoveryDecision none() {
    return new HistoricalJobAdmissionRecoveryDecision(Action.NONE, null, "");
  }

  public static HistoricalJobAdmissionRecoveryDecision recordTerminal() {
    return new HistoricalJobAdmissionRecoveryDecision(Action.RECORD_TERMINAL, null, "");
  }

  public static HistoricalJobAdmissionRecoveryDecision markCancelledAndRecordTerminal(
      String reason) {
    return new HistoricalJobAdmissionRecoveryDecision(
        Action.MARK_CANCELLED_AND_RECORD_TERMINAL, null, reason);
  }

  public static HistoricalJobAdmissionRecoveryDecision markFailedAndRecordTerminal(
      HistoricalJobStage failureStage, String reason) {
    return new HistoricalJobAdmissionRecoveryDecision(
        Action.MARK_FAILED_AND_RECORD_TERMINAL, failureStage, reason);
  }

  public enum Action {
    NONE,
    RECORD_TERMINAL,
    MARK_CANCELLED_AND_RECORD_TERMINAL,
    MARK_FAILED_AND_RECORD_TERMINAL
  }
}
