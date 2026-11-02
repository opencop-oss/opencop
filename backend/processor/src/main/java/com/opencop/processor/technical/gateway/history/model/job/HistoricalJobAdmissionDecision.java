// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HistoricalJobAdmissionDecision(
    Action action,
    String queueRole,
    List<HistoricalTracksJobSubmission> jobsToStart,
    List<HistoricalJobCancellation> jobsToCancel,
    boolean replacementQueued,
    boolean replacementSuperseded,
    boolean replacementStarted) {

  public HistoricalJobAdmissionDecision {
    Objects.requireNonNull(action, "action");
    queueRole = queueRole == null ? "" : queueRole;
    jobsToStart = List.copyOf(jobsToStart == null ? List.of() : jobsToStart);
    jobsToCancel = List.copyOf(jobsToCancel == null ? List.of() : jobsToCancel);
  }

  public static HistoricalJobAdmissionDecision startNow() {
    return new HistoricalJobAdmissionDecision(
        Action.START_NOW, "", List.of(), List.of(), false, false, false);
  }

  public static HistoricalJobAdmissionDecision queueOnly(
      String queueRole, boolean replacementQueued, boolean replacementSuperseded) {
    return new HistoricalJobAdmissionDecision(
        Action.QUEUE_ONLY,
        queueRole,
        List.of(),
        List.of(),
        replacementQueued,
        replacementSuperseded,
        false);
  }

  public static HistoricalJobAdmissionDecision noop(
      List<HistoricalTracksJobSubmission> jobsToStart,
      List<HistoricalJobCancellation> jobsToCancel,
      boolean replacementStarted) {
    return new HistoricalJobAdmissionDecision(
        Action.NOOP, "", jobsToStart, jobsToCancel, false, false, replacementStarted);
  }

  public enum Action {
    START_NOW,
    QUEUE_ONLY,
    NOOP
  }

  public record HistoricalJobCancellation(UUID jobId, String reason) {
    public HistoricalJobCancellation {
      Objects.requireNonNull(jobId, "jobId");
      Objects.requireNonNull(reason, "reason");
    }
  }
}
