// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.nudge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SocketReconciliationNudgeTest {

  @Test
  void serializesVersionOnlyPayload() {
    var objectMapper = new ObjectMapper();
    var nudge = SocketReconciliationNudge.current();

    var json = objectMapper.writeValueAsString(nudge);
    var roundTrip = objectMapper.readValue(json, SocketReconciliationNudge.class);

    assertEquals("{\"version\":1}", json);
    assertEquals(SocketReconciliationNudge.CURRENT_VERSION, roundTrip.version());
    assertEquals(1, SocketReconciliationNudge.current().version());
    assertEquals("opencop_connect_reconciliation_nudges", SocketReconciliationNudge.CHANNEL);
  }
}
