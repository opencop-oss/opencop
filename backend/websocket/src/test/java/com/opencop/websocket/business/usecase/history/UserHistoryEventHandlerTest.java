// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.core.sdk.model.history.HistoryJobPlanEvent;
import com.opencop.core.sdk.technical.gateway.history.HistoryJobDeliveryCoordinator;
import com.opencop.core.sdk.technical.gateway.history.RedisHistoricalChunkDeliveryStateRepository;
import com.opencop.websocket.ports.gateway.history.HistoricalChunkPayloadGateway;
import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class UserHistoryEventHandlerTest {

  private static final String PAYLOAD_PART_MISSING = "PAYLOAD_PART_MISSING";
  private static final String PAYLOAD_SEND_FAILED = "PAYLOAD_SEND_FAILED";
  private static final String NO_ACTIVE_HISTORY_SUBSCRIPTION =
      HistoryChunkDeliveryFailureReason.NO_ACTIVE_HISTORY_SUBSCRIPTION.wireValue();
  private static final String PAYLOAD_KEY_PREFIX = "opencop:history:";
  private static final String PAYLOAD_KEY_FIELD_NAME = "payloadKey";
  private static final String CHUNK_STATE_KEY_FIELD_NAME = "chunkStateKey";

  private final RecordingSocketProducer producer = new RecordingSocketProducer();
  private final RecordingHistoricalChunkPayloadGateway payloadGateway =
      new RecordingHistoricalChunkPayloadGateway();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Tracer tracer = mockTracer();
  private final UserHistoryChunkSender historyChunkSender =
      UserHistoryChunkSender.create(
          producer,
          objectMapper,
          payloadGateway,
          new HistoricalChunkPartKeyResolver(),
          new HistoryChunkDeliveryPrecondition(),
          new SimpleMeterRegistry(),
          tracer,
          UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
  private final HistoryJobDeliveryCoordinator jobDeliveryCoordinator =
      Mockito.mock(HistoryJobDeliveryCoordinator.class);
  private final RedisHistoricalChunkDeliveryStateRepository chunkDeliveryStateRepository =
      Mockito.mock(RedisHistoricalChunkDeliveryStateRepository.class);
  private final UserHistoryEventHandler handler =
      new UserHistoryEventHandler(
          producer,
          objectMapper,
          historyChunkSender,
          jobDeliveryCoordinator,
          chunkDeliveryStateRepository);

  @BeforeEach
  void resetDeliveryCoordinatorMock() {
    Mockito.reset(jobDeliveryCoordinator);
    Mockito.reset(chunkDeliveryStateRepository);
  }

  @Test
  void handle_forPlanEvent_sendsWithoutPayloadLookup() throws Exception {
    var event =
        new HistoryJobPlanEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            Instant.parse("2026-04-07T10:00:00Z"),
            2,
            1,
            1);

    handler.handle(event);

    assertEquals(1, producer.events.size());
    assertEquals(event.targetUserId(), producer.events.get(0).userId());
    assertEquals("/user/queue/history", producer.events.get(0).destination());
    assertArrayEquals(objectMapper.writeValueAsBytes(event), producer.events.get(0).payload());
    assertFalse(payloadGateway.called);
  }

  @Test
  void handle_forChunkStatusEventWithPayload_sendsToUserQueue() throws Exception {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any()))
        .thenReturn(true);

    handler.handle(event);

    assertEquals(3, producer.events.size());
    var sendingStatus =
        objectMapper.readValue(producer.events.get(0).payload(), HistoryChunkStatusEvent.class);
    assertEquals(HistoryChunkStatus.SENDING, sendingStatus.status());
    assertEquals(event.payloadKey(), payloadGateway.lastPayloadKey);
    assertEquals("application/x-protobuf", producer.events.get(1).headers().get("content-type"));
    assertEquals(
        HistoryEventType.HISTORY_CHUNK_DATA.getName(),
        producer.events.get(1).headers().get("history-type"));
    HistoricalChunkPayload chunkData =
        HistoricalChunkPayload.parseFrom(producer.events.get(1).payload());
    assertEquals(event.jobId(), sendingStatus.jobId());
    assertEquals(event.chunkKey(), chunkData.getChunkKey());
    assertEquals(1, chunkData.getPointCount());
    assertEquals(1, chunkData.getObjectCount());
    assertEquals(0, chunkData.getPartIndex());
    assertEquals(1, chunkData.getPartCount());
    assertTrue(chunkData.getFinalForChunk());
    var completedStatus =
        objectMapper.readValue(producer.events.get(2).payload(), HistoryChunkStatusEvent.class);
    assertEquals(HistoryChunkStatus.COMPLETED, completedStatus.status());
    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkDeliveredForJob(
            Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any());
    Mockito.verify(jobDeliveryCoordinator).onChunkDelivered(event.jobId());
  }

  @Test
  void handle_forChunkStatusEventWithoutPayload_skipsDelivery() {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.empty();
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                Mockito.eq(event.jobId()),
                Mockito.eq(event.chunkStateKey()),
                Mockito.any(),
                Mockito.eq(PAYLOAD_PART_MISSING)))
        .thenReturn(true);

    handler.handle(event);

    assertEquals(event.payloadKey(), payloadGateway.lastPayloadKey);
    assertEquals(0, producer.events.size());
    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkFailedForJob(
            Mockito.eq(event.jobId()),
            Mockito.eq(event.chunkStateKey()),
            Mockito.any(),
            Mockito.eq(PAYLOAD_PART_MISSING));
    Mockito.verify(jobDeliveryCoordinator)
        .onChunkDeliveryFailed(event.jobId(), PAYLOAD_PART_MISSING);
  }

  @Test
  void handle_forChunkStatusEvent_reportsStableFailureWhenBinaryPayloadCannotBeDelivered() {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    producer.failNextBinarySend = true;
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                Mockito.eq(event.jobId()),
                Mockito.eq(event.chunkStateKey()),
                Mockito.any(),
                Mockito.eq(PAYLOAD_SEND_FAILED)))
        .thenReturn(true);

    handler.handle(event);

    assertEquals(1, producer.events.size());
    var sendingStatus =
        assertDoesNotThrow(
            () ->
                objectMapper.readValue(
                    producer.events.get(0).payload(), HistoryChunkStatusEvent.class));
    assertEquals(HistoryChunkStatus.SENDING, sendingStatus.status());
    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkFailedForJob(
            Mockito.eq(event.jobId()),
            Mockito.eq(event.chunkStateKey()),
            Mockito.any(),
            Mockito.eq(PAYLOAD_SEND_FAILED));
    Mockito.verify(jobDeliveryCoordinator)
        .onChunkDeliveryFailed(Mockito.eq(event.jobId()), Mockito.eq(PAYLOAD_SEND_FAILED));
  }

  @Test
  void handle_forChunkStatusEvent_passesRedactedStableFailureReasonToCoordinator() {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.empty();
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                Mockito.eq(event.jobId()),
                Mockito.eq(event.chunkStateKey()),
                Mockito.any(),
                Mockito.eq(PAYLOAD_PART_MISSING)))
        .thenReturn(true);

    handler.handle(event);

    var failureReasonCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(jobDeliveryCoordinator)
        .onChunkDeliveryFailed(Mockito.eq(event.jobId()), failureReasonCaptor.capture());
    String failureReason = failureReasonCaptor.getValue();
    assertEquals(PAYLOAD_PART_MISSING, failureReason);
    assertRedactedFailureReason(failureReason);
  }

  @Test
  void doesNotMarkDeliveredWhenSendChunkReturnsFalse() {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    // Simulate the user having no active subscribed session — all sends return false.
    producer.simulateNoSession = true;
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                Mockito.eq(event.jobId()),
                Mockito.eq(event.chunkStateKey()),
                Mockito.any(),
                Mockito.eq(NO_ACTIVE_HISTORY_SUBSCRIPTION)))
        .thenReturn(true);

    handler.handle(event);

    // No frames should have been recorded because the producer reported no session.
    assertEquals(0, producer.events.size());
    // The no-session condition is terminal; only a failed transition is allowed.
    Mockito.verify(chunkDeliveryStateRepository, Mockito.never())
        .tryMarkDeliveredForJob(Mockito.any(), Mockito.any(), Mockito.any());
    Mockito.verify(jobDeliveryCoordinator, Mockito.never()).onChunkDelivered(Mockito.any());
    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkFailedForJob(
            Mockito.eq(event.jobId()),
            Mockito.eq(event.chunkStateKey()),
            Mockito.any(),
            Mockito.eq(NO_ACTIVE_HISTORY_SUBSCRIPTION));
    Mockito.verify(jobDeliveryCoordinator)
        .onChunkDeliveryFailed(
            Mockito.eq(event.jobId()), Mockito.eq(NO_ACTIVE_HISTORY_SUBSCRIPTION));
  }

  @Test
  void doesNotCallFailureCoordinatorWhenChunkFailureAlreadyTerminal() {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    producer.simulateNoSession = true;
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkFailedForJob(
                Mockito.eq(event.jobId()),
                Mockito.eq(event.chunkStateKey()),
                Mockito.any(),
                Mockito.eq(NO_ACTIVE_HISTORY_SUBSCRIPTION)))
        .thenReturn(false);

    handler.handle(event);

    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkFailedForJob(
            Mockito.eq(event.jobId()),
            Mockito.eq(event.chunkStateKey()),
            Mockito.any(),
            Mockito.eq(NO_ACTIVE_HISTORY_SUBSCRIPTION));
    Mockito.verify(jobDeliveryCoordinator, Mockito.never())
        .onChunkDeliveryFailed(Mockito.any(), Mockito.any());
  }

  @Test
  void marksDeliveredWhenSendChunkReturnsTrue() throws Exception {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any()))
        .thenReturn(true);

    handler.handle(event);

    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkDeliveredForJob(
            Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any());
    Mockito.verify(jobDeliveryCoordinator).onChunkDelivered(event.jobId());
  }

  @Test
  void sharedContentChunkStateAllowsEachJobToRecordDelivery() {
    var firstJob = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    var secondJob = UUID.fromString("550e8400-e29b-41d4-a716-446655440104");
    var firstEvent = chunkStatusEvent(firstJob);
    var secondEvent = chunkStatusEvent(secondJob);
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                Mockito.eq(firstJob), Mockito.eq(firstEvent.chunkStateKey()), Mockito.any()))
        .thenReturn(true);
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                Mockito.eq(secondJob), Mockito.eq(secondEvent.chunkStateKey()), Mockito.any()))
        .thenReturn(true);

    handler.handle(firstEvent);
    handler.handle(secondEvent);

    assertEquals(firstEvent.chunkStateKey(), secondEvent.chunkStateKey());
    Mockito.verify(jobDeliveryCoordinator).onChunkDelivered(firstJob);
    Mockito.verify(jobDeliveryCoordinator).onChunkDelivered(secondJob);
  }

  @Test
  void doesNotCallDeliveryCoordinatorWhenDeliveryAlreadyMarkedForJob() throws Exception {
    var event = chunkStatusEvent();
    payloadGateway.response = Optional.of(chunkPayloadBytes());
    Mockito.when(
            chunkDeliveryStateRepository.tryMarkDeliveredForJob(
                Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any()))
        .thenReturn(false);

    handler.handle(event);

    Mockito.verify(chunkDeliveryStateRepository)
        .tryMarkDeliveredForJob(
            Mockito.eq(event.jobId()), Mockito.eq(event.chunkStateKey()), Mockito.any());
    Mockito.verify(jobDeliveryCoordinator, Mockito.never()).onChunkDelivered(Mockito.any());
  }

  private static HistoryChunkStatusEvent chunkStatusEvent() {
    return chunkStatusEvent(UUID.fromString("550e8400-e29b-41d4-a716-446655440102"));
  }

  private static HistoryChunkStatusEvent chunkStatusEvent(UUID jobId) {
    return new HistoryChunkStatusEvent(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
        jobId,
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

  private static byte[] chunkPayloadBytes() {
    return opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload.newBuilder()
        .setChunkKey("chunk-1")
        .setChunkIndex(1)
        .setFromEpochMillis(Instant.parse("2026-04-06T10:00:00Z").toEpochMilli())
        .setToEpochMillis(Instant.parse("2026-04-06T11:00:00Z").toEpochMilli())
        .setResolution(
            opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkResolution
                .HISTORICAL_CHUNK_RESOLUTION_HOUR_1)
        .setProfile(
            opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkProfile
                .HISTORICAL_CHUNK_PROFILE_DETAIL)
        .setPointCount(1)
        .setObjectCount(1)
        .setPartIndex(0)
        .setPartCount(1)
        .setFinalForChunk(true)
        .addElements(
            opencop.core.proto.deckgl.ElementProtos.ElementMessage.newBuilder()
                .setId(
                    com.google.protobuf.ByteString.copyFrom(
                        uuidBytes("550e8400-e29b-41d4-a716-446655440110")))
                .setSource(
                    com.google.protobuf.ByteString.copyFrom(
                        uuidBytes("550e8400-e29b-41d4-a716-446655440111")))
                .setObjectId("obj-1")
                .setType("a-f-G-U-C")
                .setDisplayName("Object 1")
                .setStart(10L)
                .setTime(20L)
                .setStale(30L)
                .addPositions(1.0)
                .addPositions(2.0)
                .putValues(
                    "speed",
                    opencop.core.proto.deckgl.ElementProtos.Value.newBuilder()
                        .setDouble(12.5)
                        .build())
                .build())
        .build()
        .toByteArray();
  }

  private static byte[] uuidBytes(String value) {
    var uuid = UUID.fromString(value);
    var buffer = java.nio.ByteBuffer.allocate(16);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }

  private static void assertRedactedFailureReason(String failureReason) {
    assertFalse(failureReason.contains(PAYLOAD_KEY_PREFIX));
    assertFalse(failureReason.contains(PAYLOAD_KEY_FIELD_NAME));
    assertFalse(failureReason.contains(CHUNK_STATE_KEY_FIELD_NAME));
  }

  private static final class RecordingHistoricalChunkPayloadGateway
      implements HistoricalChunkPayloadGateway {

    private boolean called;
    private String lastPayloadKey;
    private Optional<byte[]> response = Optional.empty();

    @Override
    public Optional<byte[]> find(String payloadKey) {
      called = true;
      lastPayloadKey = payloadKey;
      return response;
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      called = true;
      lastPayloadKey = payloadKeys.getFirst();
      return payloadKeys.stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  key -> key,
                  key -> response,
                  (left, right) -> right,
                  java.util.LinkedHashMap::new));
    }
  }

  private static final class RecordingSocketProducer implements SocketProducer {

    private final List<SentEvent> events = new ArrayList<>();
    private boolean failNextBinarySend;

    /** When true, the 5-arg sendToUser returns false simulating no active subscribed session. */
    private boolean simulateNoSession;

    @Override
    public void sendToUser(UUID userId, String destination, byte[] event, boolean compress) {
      this.events.add(new SentEvent(userId, destination, event, Map.of()));
    }

    @Override
    public boolean sendToUser(
        UUID userId,
        String destination,
        byte[] event,
        Map<String, String> headers,
        boolean compress) {
      if (simulateNoSession) {
        return false;
      }
      Map<String, String> safeHeaders = headers != null ? headers : Map.of();
      this.events.add(new SentEvent(userId, destination, event, safeHeaders));
      return true;
    }

    @Override
    public CompletableFuture<SendAttemptResult> sendToUserAwait(
        UUID userId,
        String destination,
        byte[] event,
        Map<String, String> headers,
        boolean compress) {
      Map<String, String> safeHeaders = headers != null ? headers : Map.of();
      if (failNextBinarySend && "application/x-protobuf".equals(safeHeaders.get("content-type"))) {
        failNextBinarySend = false;
        return CompletableFuture.failedFuture(new IOException("binary send failed"));
      }
      this.events.add(new SentEvent(userId, destination, event, safeHeaders));
      return CompletableFuture.completedFuture(SendAttemptResult.DELIVERED);
    }

    @Override
    public void broadcast(String topic, byte[] event, boolean compress) {}

    private record SentEvent(
        UUID userId, String destination, byte[] payload, Map<String, String> headers) {}
  }

  private static Tracer mockTracer() {
    Tracer tracer = Mockito.mock(Tracer.class);
    SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class);
    Span span = Mockito.mock(Span.class);
    Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.setParent(Mockito.any(Context.class))).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.startSpan()).thenReturn(span);
    return tracer;
  }
}
