// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.opencop.core.sdk.model.SubjectType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoryEventTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void chunkStatusEvent_roundTripsThroughHistoryEventTypeInfo() throws Exception {
    var event =
        new HistoryChunkStatusEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            "chunk-1",
            "opencop:history:chunk-state:test",
            1,
            "opencop:history:payload:test",
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            1,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);

    HistoryEvent deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(event), HistoryEvent.class);

    var chunkStatus = assertInstanceOf(HistoryChunkStatusEvent.class, deserialized);
    assertEquals(event, chunkStatus);
    assertEquals(HistoryEventType.HISTORY_CHUNK_STATUS, chunkStatus.type());
  }

  @Test
  void progressCompletedAndFailed_roundTripThroughHistoryEventTypeInfo() throws Exception {
    HistoryEvent progress =
        new HistoryJobProgressEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            10,
            4,
            6,
            7,
            1,
            2,
            70);
    HistoryEvent completed =
        new HistoryJobCompletedEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            10,
            4,
            6,
            10,
            0);
    HistoryEvent failed =
        new HistoryJobFailedEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            HistoricalJobStage.EXECUTION_FAILED_UNKNOWN.name(),
            "boom");

    assertEquals(
        progress,
        objectMapper.readValue(objectMapper.writeValueAsBytes(progress), HistoryEvent.class));
    assertEquals(
        completed,
        objectMapper.readValue(objectMapper.writeValueAsBytes(completed), HistoryEvent.class));
    assertEquals(
        failed, objectMapper.readValue(objectMapper.writeValueAsBytes(failed), HistoryEvent.class));
  }

  @Test
  void chunkStatusEvent_deserializesLegacyPayloadWithoutTraceContext() throws Exception {
    String legacyJson =
        """
        {
          "type":"history.chunk.status",
          "targetUserId":"550e8400-e29b-41d4-a716-446655440100",
          "requestId":"550e8400-e29b-41d4-a716-446655440101",
          "jobId":"550e8400-e29b-41d4-a716-446655440102",
          "subjectType":"SOURCE",
          "subjectId":"550e8400-e29b-41d4-a716-446655440103",
          "chunkKey":"chunk-1",
          "chunkStateKey":"state-1",
          "chunkIndex":1,
          "payloadKey":"payload-1",
          "chunkFrom":"2026-04-06T10:00:00Z",
          "chunkTo":"2026-04-06T11:00:00Z",
          "resolution":"HOUR_1",
          "samplingVersion":"v1_latest_in_bucket",
          "cached":true,
          "partCount":1,
          "status":"AVAILABLE_FOR_DELIVERY"
        }
        """;

    HistoryEvent deserialized = objectMapper.readValue(legacyJson, HistoryEvent.class);
    var chunkStatus = assertInstanceOf(HistoryChunkStatusEvent.class, deserialized);
    assertEquals(Map.of(), chunkStatus.traceContext());
    assertEquals("chunk-1", chunkStatus.chunkKey());
    assertEquals(1, chunkStatus.chunkIndex());
  }

  @Test
  void chunkStatusEvent_roundTripPreservesValidTraceparent() throws Exception {
    var traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    var event =
        new HistoryChunkStatusEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            "chunk-1",
            "opencop:history:chunk-state:test",
            1,
            "opencop:history:payload:test",
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            1,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY,
            Map.of("traceparent", traceparent));

    HistoryEvent deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(event), HistoryEvent.class);
    var chunkStatus = assertInstanceOf(HistoryChunkStatusEvent.class, deserialized);
    assertEquals(traceparent, chunkStatus.traceContext().get("traceparent"));
  }

  @Test
  void chunkStatusEvent_constructorRejectsMalformedOrDisallowedTraceContext() {
    var event =
        new HistoryChunkStatusEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            "chunk-1",
            "opencop:history:chunk-state:test",
            1,
            "opencop:history:payload:test",
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            1,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY,
            Map.of(
                "traceparent", "bad",
                "x-user-id", "550e8400-e29b-41d4-a716-446655440120"));

    assertEquals(Map.of(), event.traceContext());
  }

  @Test
  void historicalJobStage_appendsCancellationStagesAfterExistingConstants() {
    var names = Arrays.stream(HistoricalJobStage.values()).map(Enum::name).toList();
    var expectedOrderBeforeCancellationStages =
        List.of(
            "INGRESS_RECEIVED",
            "INGRESS_QUEUED",
            "PLANNING",
            "PLANNED",
            "EXECUTION_RUNNING",
            "EXECUTION_COMPLETED",
            "DELIVERY_PENDING",
            "INGRESS_REJECTED",
            "TIMEOUT",
            "DELIVERY_FAILED",
            "EXECUTION_FAILED_UNKNOWN",
            "EXECUTION_FAILED_STORAGE",
            "EXECUTION_FAILED_TIMEOUT",
            "EXECUTION_FAILED_INTERRUPTED",
            "EXECUTION_FAILED_UNEXPECTED");

    assertEquals(
        expectedOrderBeforeCancellationStages,
        names.subList(0, expectedOrderBeforeCancellationStages.size()));
    assertEquals("CANCELLATION_REQUESTED", names.get(names.size() - 2));
    assertEquals("CANCELLED", names.get(names.size() - 1));
  }
}
