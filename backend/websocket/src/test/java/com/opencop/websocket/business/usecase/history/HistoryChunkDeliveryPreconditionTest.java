// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryChunkDeliveryPreconditionTest {

  private final HistoryChunkDeliveryPrecondition precondition =
      new HistoryChunkDeliveryPrecondition();

  @Test
  void validate_whenPayloadKeyAndJobIdAreValid_returnsValidResult() {
    var result = precondition.validate(availableEvent());

    assertThat(result.valid()).isTrue();
    assertThat(result.failureReason()).isEmpty();
  }

  @Test
  void validate_whenPayloadKeyHasWrongPrefix_returnsPayloadKeyInvalid() {
    var result =
        precondition.validate(availableEventWithPayloadKey("history:payload:not-a-uuid:0"));

    assertThat(result.valid()).isFalse();
    assertThat(result.failureReason())
        .contains(HistoryChunkDeliveryFailureReason.PAYLOAD_KEY_INVALID);
  }

  @Test
  void validate_whenSubjectBearingPayloadKeyIsIncomplete_returnsPayloadKeyInvalid() {
    var result =
        precondition.validate(
            availableEventWithPayloadKey(
                "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440103"));

    assertThat(result.valid()).isFalse();
    assertThat(result.failureReason())
        .contains(HistoryChunkDeliveryFailureReason.PAYLOAD_KEY_INVALID);
  }

  @Test
  void validate_whenJobIdIsMissing_returnsJobIdMissing() {
    var event = availableEvent();

    var result =
        precondition.validate(
            new HistoryChunkStatusEvent(
                event.targetUserId(),
                event.requestId(),
                null,
                event.subjectType(),
                event.subjectId(),
                event.chunkKey(),
                event.chunkStateKey(),
                event.chunkIndex(),
                event.payloadKey(),
                event.chunkFrom(),
                event.chunkTo(),
                event.resolution(),
                event.samplingVersion(),
                event.cached(),
                event.partCount(),
                event.status()));

    assertThat(result.valid()).isFalse();
    assertThat(result.failureReason()).contains(HistoryChunkDeliveryFailureReason.JOB_ID_MISSING);
  }

  private static HistoryChunkStatusEvent availableEventWithPayloadKey(String payloadKey) {
    var event = availableEvent();
    return new HistoryChunkStatusEvent(
        event.targetUserId(),
        event.requestId(),
        event.jobId(),
        event.subjectType(),
        event.subjectId(),
        event.chunkKey(),
        event.chunkStateKey(),
        event.chunkIndex(),
        payloadKey,
        event.chunkFrom(),
        event.chunkTo(),
        event.resolution(),
        event.samplingVersion(),
        event.cached(),
        event.partCount(),
        event.status());
  }

  private static HistoryChunkStatusEvent availableEvent() {
    return new HistoryChunkStatusEvent(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
        "chunk-1",
        "opencop:history:chunk-state:test",
        1,
        "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440102:HOUR_1:v1:v1_latest_in_bucket:0000000000000000000000000000000000000000000000000000000000000000:2026-05-14T11:00:00Z",
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.HOUR_1,
        "v1_latest_in_bucket",
        true,
        1,
        HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);
  }
}
