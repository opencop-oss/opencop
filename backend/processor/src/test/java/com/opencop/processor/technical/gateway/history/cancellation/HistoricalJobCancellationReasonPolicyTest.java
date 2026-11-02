// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.cancellation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.history.HistoricalJobCancellationReasons;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalJobCancellationReasonPolicyTest {

  @Test
  void requestedCancellationReason_whenStateHasReason_usesStateReason() {
    String reason =
        HistoricalJobCancellationReasonPolicy.requestedCancellationReason(
            "operator cancelled", UUID.randomUUID());

    assertEquals("operator cancelled", reason);
  }

  @Test
  void requestedCancellationReason_whenReplacementExists_usesSupersededReason() {
    UUID replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    String reason =
        HistoricalJobCancellationReasonPolicy.requestedCancellationReason(
            null, replacementRequestId);

    assertEquals(
        HistoricalJobCancellationReasons.supersededByRequest(replacementRequestId), reason);
  }

  @Test
  void requestedCancellationReason_whenNoContext_usesSameJobRedeliveryReason() {
    String reason =
        HistoricalJobCancellationReasonPolicy.requestedCancellationReason(null, (UUID) null);

    assertEquals("Historical job cancelled before processor start", reason);
  }

  @Test
  void requestedCancellationReason_whenNoFallback_usesDefaultReason() {
    String reason = HistoricalJobCancellationReasonPolicy.requestedCancellationReason(null);

    assertEquals("Historical job cancelled", reason);
  }
}
