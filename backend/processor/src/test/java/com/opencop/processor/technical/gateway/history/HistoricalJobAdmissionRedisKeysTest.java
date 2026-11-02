// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalJobAdmissionRedisKeysTest {
  @Test
  void registerScriptKeys_returnsKeysInExpectedOrderWithMetaKeyThenPayloadKey() {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");

    assertEquals(
        List.of(
            "opencop:history:admission:user:%s:active-request".formatted(userId),
            "opencop:history:admission:user:%s:active-running".formatted(userId),
            "opencop:history:admission:user:%s:active-backlog".formatted(userId),
            "opencop:history:admission:user:%s:replacement-request".formatted(userId),
            "opencop:history:admission:user:%s:replacement-jobs".formatted(userId),
            "opencop:history:admission:job-meta:%s".formatted(jobId),
            "opencop:history:job-payload:%s".formatted(jobId)),
        HistoricalJobAdmissionRedisKeys.registerScriptKeys(userId, jobId));
  }

  @Test
  void recordOutcomeScriptKeys_returnsRegisterKeysWithoutPayload() {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011");

    assertEquals(
        List.of(
            "opencop:history:admission:user:%s:active-request".formatted(userId),
            "opencop:history:admission:user:%s:active-running".formatted(userId),
            "opencop:history:admission:user:%s:active-backlog".formatted(userId),
            "opencop:history:admission:user:%s:replacement-request".formatted(userId),
            "opencop:history:admission:user:%s:replacement-jobs".formatted(userId),
            "opencop:history:admission:job-meta:%s".formatted(jobId)),
        HistoricalJobAdmissionRedisKeys.recordOutcomeScriptKeys(userId, jobId));
  }

  @Test
  void cleanupScriptKeys_returnsKeysInExpectedOrderWithPayloadThenMeta() {
    var userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
    var jobId = "bad-job-id";

    assertEquals(
        List.of(
            "opencop:history:admission:user:%s:active-request".formatted(userId),
            "opencop:history:admission:user:%s:active-running".formatted(userId),
            "opencop:history:admission:user:%s:active-backlog".formatted(userId),
            "opencop:history:admission:user:%s:replacement-request".formatted(userId),
            "opencop:history:admission:user:%s:replacement-jobs".formatted(userId),
            "opencop:history:job-payload:%s".formatted(jobId),
            "opencop:history:admission:job-meta:%s".formatted(jobId)),
        HistoricalJobAdmissionRedisKeys.cleanupScriptKeys(userId, jobId));
  }

  @Test
  void activeRunningScanPattern_returnsAdmissionRunningPattern() {
    assertEquals(
        "opencop:history:admission:user:*:active-running",
        HistoricalJobAdmissionRedisKeys.activeRunningScanPattern());
  }
}
