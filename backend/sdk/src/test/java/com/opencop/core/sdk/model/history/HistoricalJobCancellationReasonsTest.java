// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalJobCancellationReasonsTest {

  @Test
  void supersededByRequest_prefixConstantMatchesExpectedValue() {
    assertEquals(
        "Superseded by request ", HistoricalJobCancellationReasons.SUPERSEDED_BY_REQUEST_PREFIX);
  }

  @Test
  void supersededByRequest_formatsReasonWithExpectedPrefix() {
    UUID replacementRequestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440777");

    String result = HistoricalJobCancellationReasons.supersededByRequest(replacementRequestId);

    assertEquals("Superseded by request 550e8400-e29b-41d4-a716-446655440777", result);
  }

  @Test
  void supersededByRequest_rejectsNullReplacementRequestId() {
    assertThrows(
        NullPointerException.class,
        () -> HistoricalJobCancellationReasons.supersededByRequest(null));
  }
}
