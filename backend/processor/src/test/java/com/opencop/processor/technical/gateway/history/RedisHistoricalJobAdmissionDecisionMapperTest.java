// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.Action;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisHistoricalJobAdmissionDecisionMapperTest {

  private final RedisHistoricalJobAdmissionDecisionMapper mapper =
      new RedisHistoricalJobAdmissionDecisionMapper();

  @Test
  void parse_nullOrEmptyResult_returnsEmptyParsedResult() {
    assertTrue(mapper.parse(null).isEmpty());
    assertTrue(mapper.parse(List.of()).isEmpty());
  }

  @Test
  void parse_extractsAllScriptFields() {
    var supersededJobId = UUID.randomUUID();
    var promotedJobId = UUID.randomUUID();

    var parsed =
        mapper.parse(
            List.of(
                Action.NOOP.name(),
                "QUEUED_REPLACEMENT",
                "1",
                "0",
                "1",
                supersededJobId.toString(),
                promotedJobId.toString()));

    assertEquals(Action.NOOP, parsed.action());
    assertEquals("QUEUED_REPLACEMENT", parsed.queueRole());
    assertTrue(parsed.replacementQueued());
    assertFalse(parsed.replacementSuperseded());
    assertTrue(parsed.replacementStarted());
    assertEquals(supersededJobId.toString(), parsed.supersededJobIds());
    assertEquals(promotedJobId.toString(), parsed.jobsToStartIds());
  }

  @Test
  void parse_unknownActionDefaultsToNoop() {
    var parsed = mapper.parse(List.of("UNKNOWN_ACTION"));

    assertEquals(Action.NOOP, parsed.action());
  }

  @Test
  void parse_omittedTrailingFieldsDefaultToEmptyOrFalse() {
    var parsed = mapper.parse(List.of(Action.QUEUE_ONLY.name(), "ACTIVE_REQUEST_BACKLOG"));

    assertEquals(Action.QUEUE_ONLY, parsed.action());
    assertEquals("ACTIVE_REQUEST_BACKLOG", parsed.queueRole());
    assertFalse(parsed.replacementQueued());
    assertFalse(parsed.replacementSuperseded());
    assertFalse(parsed.replacementStarted());
    assertEquals("", parsed.supersededJobIds());
    assertEquals("", parsed.jobsToStartIds());
  }

  @Test
  void toDecision_startNow_ignoresResolvedLists() {
    var parsed = mapper.parse(List.of(Action.START_NOW.name()));

    HistoricalJobAdmissionDecision decision = mapper.toDecision(parsed, List.of(), List.of());

    assertEquals(Action.START_NOW, decision.action());
    assertTrue(decision.jobsToStart().isEmpty());
    assertTrue(decision.jobsToCancel().isEmpty());
  }

  @Test
  void toDecision_queueOnly_carriesQueueRoleAndReplacementFlagsAndCancellations() {
    var parsed =
        mapper.parse(
            List.of(Action.QUEUE_ONLY.name(), "QUEUED_REPLACEMENT", "1", "1", "0", "", ""));
    var supersededJobId = UUID.randomUUID();
    var jobsToCancel =
        List.of(
            new HistoricalJobAdmissionDecision.HistoricalJobCancellation(
                supersededJobId,
                RedisHistoricalJobAdmissionQueuedPayloads.SUPERSEDED_BY_REPLACEMENT_REASON));

    var decision = mapper.toDecision(parsed, jobsToCancel, List.of());

    assertEquals(Action.QUEUE_ONLY, decision.action());
    assertEquals("QUEUED_REPLACEMENT", decision.queueRole());
    assertTrue(decision.replacementQueued());
    assertTrue(decision.replacementSuperseded());
    assertFalse(decision.replacementStarted());
    assertTrue(decision.jobsToStart().isEmpty());
    assertEquals(jobsToCancel, decision.jobsToCancel());
  }

  @Test
  void toDecision_noop_carriesReplacementStartedAndBothLists() {
    var parsed = mapper.parse(List.of(Action.NOOP.name(), "", "0", "0", "1", "", ""));

    var decision = mapper.toDecision(parsed, List.of(), List.of());

    assertEquals(Action.NOOP, decision.action());
    assertTrue(decision.replacementStarted());
  }
}
