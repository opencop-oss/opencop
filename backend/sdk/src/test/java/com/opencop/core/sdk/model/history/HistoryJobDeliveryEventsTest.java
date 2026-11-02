// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.technical.model.history.HistoricalJobDeliverySnapshot;
import com.opencop.core.sdk.technical.model.history.HistoryJobDeliveryEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryJobDeliveryEventsTest {

  @Test
  void progress_matchesDeliveredDrivenPercentAndRemaining() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
    UUID request = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
    UUID job = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    UUID subject = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
    var snap =
        new HistoricalJobDeliverySnapshot(
            user, request, job, SubjectType.SOURCE, subject, 10, 3, 7, 1, 4, true, true, null);
    var progress = HistoryJobDeliveryEvents.progress(snap);
    assertEquals(4, progress.deliveredChunks());
    assertEquals(1, progress.failedChunks());
    assertEquals(5, progress.remainingChunks());
    assertEquals(40, progress.percentComplete());
  }

  @Test
  void progress_whenTotalZero_isComplete() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
    UUID request = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
    UUID job = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    UUID subject = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
    var snap =
        new HistoricalJobDeliverySnapshot(
            user, request, job, SubjectType.SOURCE, subject, 0, 0, 0, 0, 0, true, true, null);
    var progress = HistoryJobDeliveryEvents.progress(snap);
    assertEquals(100, progress.percentComplete());
    assertEquals(0, progress.remainingChunks());
  }

  @Test
  void failed_usesSnapshotFailureReason() {
    UUID user = UUID.fromString("550e8400-e29b-41d4-a716-446655440100");
    UUID request = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
    UUID job = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    UUID subject = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
    var snap =
        new HistoricalJobDeliverySnapshot(
            user,
            request,
            job,
            SubjectType.SOURCE,
            subject,
            10,
            3,
            7,
            1,
            4,
            true,
            true,
            "missing payload");

    var failed = HistoryJobDeliveryEvents.failed(snap, "DELIVERY_FAILED");

    assertEquals("DELIVERY_FAILED", failed.stage());
    assertEquals("missing payload", failed.reason());
  }
}
