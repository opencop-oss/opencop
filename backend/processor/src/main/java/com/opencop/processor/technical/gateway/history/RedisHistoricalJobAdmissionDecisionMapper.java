// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.HistoricalJobCancellation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class RedisHistoricalJobAdmissionDecisionMapper {

  private static final Logger log =
      LoggerFactory.getLogger(RedisHistoricalJobAdmissionDecisionMapper.class);

  ParsedScriptResult parse(List<Object> result) {
    if (result == null || result.isEmpty()) {
      return ParsedScriptResult.EMPTY;
    }
    Action action = actionAt(result);
    String queueRole = stringAt(result, 1);
    boolean replacementQueued = "1".equals(stringAt(result, 2));
    boolean replacementSuperseded = "1".equals(stringAt(result, 3));
    boolean replacementStarted = "1".equals(stringAt(result, 4));
    String supersededJobIds = stringAt(result, 5);
    String jobsToStartIds = stringAt(result, 6);
    return new ParsedScriptResult(
        action,
        queueRole,
        replacementQueued,
        replacementSuperseded,
        replacementStarted,
        supersededJobIds,
        jobsToStartIds);
  }

  HistoricalJobAdmissionDecision toDecision(
      ParsedScriptResult parsed,
      List<HistoricalJobCancellation> jobsToCancel,
      List<HistoricalTracksJobSubmission> jobsToStart) {
    switch (parsed.action()) {
      case START_NOW:
        return HistoricalJobAdmissionDecision.startNow();
      case QUEUE_ONLY:
        return new HistoricalJobAdmissionDecision(
            Action.QUEUE_ONLY,
            parsed.queueRole(),
            List.of(),
            jobsToCancel,
            parsed.replacementQueued(),
            parsed.replacementSuperseded(),
            false);
      case NOOP:
        return new HistoricalJobAdmissionDecision(
            Action.NOOP, "", jobsToStart, jobsToCancel, false, false, parsed.replacementStarted());
    }
    throw new IllegalStateException("Unsupported historical admission action: " + parsed.action());
  }

  private static Action actionAt(List<Object> result) {
    String action = stringAt(result, 0);
    if (action.isBlank()) {
      return Action.NOOP;
    }
    try {
      return Action.valueOf(action);
    } catch (IllegalArgumentException ignored) {
      log.warn("Unknown historical admission action returned by Lua script: {}", action);
      return Action.NOOP;
    }
  }

  private static String stringAt(List<Object> result, int index) {
    Object value = result.size() > index ? result.get(index) : "";
    return value == null ? "" : value.toString();
  }

  record ParsedScriptResult(
      Action action,
      String queueRole,
      boolean replacementQueued,
      boolean replacementSuperseded,
      boolean replacementStarted,
      String supersededJobIds,
      String jobsToStartIds) {

    static final ParsedScriptResult EMPTY =
        new ParsedScriptResult(Action.NOOP, "", false, false, false, "", "");

    boolean isEmpty() {
      return this == EMPTY;
    }
  }
}
