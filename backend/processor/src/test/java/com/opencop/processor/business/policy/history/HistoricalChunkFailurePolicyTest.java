// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalChunkFailurePolicyTest {

  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");

  @Test
  void evaluate_whenThresholdsAreNotExceeded_continuesExecution() {
    var policy = new HistoricalChunkFailurePolicy(2, 0.5d);

    var decision = policy.evaluate(JOB_ID, 2, 2, 5);

    assertFalse(decision.shouldAbort());
    assertFalse(decision.consecutiveThresholdExceeded());
    assertFalse(decision.ratioThresholdExceeded());
    assertEquals(2, decision.consecutiveFailures());
    assertEquals(2L, decision.failedChunks());
    assertEquals(5, decision.totalChunks());
    assertEquals(0.4d, decision.failureRatio());
    assertNull(decision.reason());
  }

  @Test
  void evaluate_whenConsecutiveThresholdIsExceeded_abortsWithConsecutiveFlag() {
    var policy = new HistoricalChunkFailurePolicy(2, 0.9d);

    var decision = policy.evaluate(JOB_ID, 3, 3, 10);

    assertTrue(decision.shouldAbort());
    assertTrue(decision.consecutiveThresholdExceeded());
    assertFalse(decision.ratioThresholdExceeded());
    assertEquals(0.3d, decision.failureRatio());
    assertEquals(
        "Aborting job 550e8400-e29b-41d4-a716-446655440100: failure ratio exceeded threshold "
            + "(consecutiveFailures=3, failedChunks=3, totalChunks=10, ratio=0.3000)",
        decision.reason());
  }

  @Test
  void evaluate_whenConsecutiveThresholdIsDisabled_doesNotAbortForConsecutiveFailuresOnly() {
    var policy = new HistoricalChunkFailurePolicy(0, 0.9d);

    var decision = policy.evaluate(JOB_ID, 10, 2, 10);

    assertFalse(decision.shouldAbort());
    assertFalse(decision.consecutiveThresholdExceeded());
    assertFalse(decision.ratioThresholdExceeded());
  }

  @Test
  void evaluate_whenFailureRatioIsExceeded_abortsWithRatioFlag() {
    var policy = new HistoricalChunkFailurePolicy(10, 0.2d);

    var decision = policy.evaluate(JOB_ID, 1, 3, 10);

    assertTrue(decision.shouldAbort());
    assertFalse(decision.consecutiveThresholdExceeded());
    assertTrue(decision.ratioThresholdExceeded());
    assertEquals(0.3d, decision.failureRatio());
  }

  @Test
  void evaluate_whenBothThresholdsAreExceeded_setsBothAbortFlags() {
    var policy = new HistoricalChunkFailurePolicy(2, 0.2d);

    var decision = policy.evaluate(JOB_ID, 3, 3, 10);

    assertTrue(decision.shouldAbort());
    assertTrue(decision.consecutiveThresholdExceeded());
    assertTrue(decision.ratioThresholdExceeded());
  }
}
