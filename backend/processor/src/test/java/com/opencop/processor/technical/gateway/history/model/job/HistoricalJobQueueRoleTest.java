// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoricalJobQueueRoleTest {

  @Test
  void validate_acceptsSupportedQueueRoles() {
    assertDoesNotThrow(
        () -> HistoricalJobQueueRole.validate(HistoricalJobQueueRole.DISPATCH_READY));
    assertDoesNotThrow(
        () -> HistoricalJobQueueRole.validate(HistoricalJobQueueRole.ACTIVE_REQUEST_BACKLOG));
    assertDoesNotThrow(
        () -> HistoricalJobQueueRole.validate(HistoricalJobQueueRole.QUEUED_REPLACEMENT));
  }

  @Test
  void validate_rejectsUnsupportedQueueRole() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> HistoricalJobQueueRole.validate("BAD_ROLE"));

    assertEquals("Unsupported queue role: BAD_ROLE", thrown.getMessage());
  }

  @Test
  void normalizeForStatus_defaultsQueuedStatesToDispatchReady() {
    assertEquals(
        HistoricalJobQueueRole.DISPATCH_READY,
        HistoricalJobQueueRole.normalizeForStatus(
            HistoricalJobStatus.QUEUED, HistoricalJobQueueRole.QUEUED_REPLACEMENT));
  }

  @Test
  void normalizeForStatus_preservesQueueRoleForNonQueuedStates() {
    assertEquals(
        HistoricalJobQueueRole.QUEUED_REPLACEMENT,
        HistoricalJobQueueRole.normalizeForStatus(
            HistoricalJobStatus.RUNNING, HistoricalJobQueueRole.QUEUED_REPLACEMENT));
  }
}
