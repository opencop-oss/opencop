// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.websocket.ports.gateway.history.HistoricalChunkPayloadGateway;
import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class UserHistoryChunkSenderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HistoricalChunkPartKeyResolver partKeyResolver =
      new HistoricalChunkPartKeyResolver();
  private static final String MALFORMED_SUBJECT_BEARING_PAYLOAD_KEY =
      "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440103";
  private static final String PAYLOAD_KEY_PREFIX = "opencop:history:";
  private static final String PAYLOAD_KEY_FIELD_NAME = "payloadKey";
  private static final String CHUNK_STATE_KEY_FIELD_NAME = "chunkStateKey";
  private static final String PAYLOAD_KEY_INVALID = "PAYLOAD_KEY_INVALID";
  private static final String JOB_ID_MISSING = "JOB_ID_MISSING";
  private static final String PAYLOAD_PART_MISSING = "PAYLOAD_PART_MISSING";
  private static final String PAYLOAD_DECODE_FAILED = "PAYLOAD_DECODE_FAILED";
  private static final String PAYLOAD_SEND_FAILED = "PAYLOAD_SEND_FAILED";
  private static final String PAYLOAD_SEND_TIMEOUT = "PAYLOAD_SEND_TIMEOUT";
  private static final String PAYLOAD_SEND_INTERRUPTED = "PAYLOAD_SEND_INTERRUPTED";
  private static final String NO_ACTIVE_HISTORY_SUBSCRIPTION =
      HistoryChunkDeliveryFailureReason.NO_ACTIVE_HISTORY_SUBSCRIPTION.wireValue();

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

  private static HistoryChunkStatusEvent availableEvent3Parts() {
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
        3,
        HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);
  }

  private static byte[] validPayloadBytes() {
    return validPayloadBytesForPart(0, 1);
  }

  private static byte[] validPayloadBytesForPart(int partIndex, int partCount) {
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
        .setPartIndex(partIndex)
        .setPartCount(partCount)
        .setFinalForChunk(partIndex == partCount - 1)
        .build()
        .toByteArray();
  }

  private UserHistoryChunkSender senderWith(
      SocketProducer producer, HistoricalChunkPayloadGateway gateway) {
    return UserHistoryChunkSender.create(
        producer,
        objectMapper,
        gateway,
        partKeyResolver,
        new HistoryChunkDeliveryPrecondition(),
        new SimpleMeterRegistry(),
        mockTracer(),
        UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
  }

  private UserHistoryChunkSender senderWithThreePartGateway(SocketProducer producer) {
    return UserHistoryChunkSender.create(
        producer,
        objectMapper,
        fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
        partKeyResolver,
        new HistoryChunkDeliveryPrecondition(),
        new SimpleMeterRegistry(),
        mockTracer(),
        UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
  }

  private UserHistoryChunkSender senderWithGauge(
      SocketProducer producer, SimpleMeterRegistry meterRegistry) {
    return UserHistoryChunkSender.create(
        producer,
        objectMapper,
        fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
        partKeyResolver,
        new HistoryChunkDeliveryPrecondition(),
        meterRegistry,
        mockTracer(),
        UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
  }

  private static void awaitCondition(
      java.util.function.BooleanSupplier condition, Duration timeout, String description) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      if (condition.getAsBoolean()) return;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted waiting for " + description, e);
      }
    }
    throw new AssertionError("Timed out waiting for: " + description);
  }

  @Test
  void returnsTrueWhenAllFramesDelivered() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(availableEvent());
    assertTrue(result.success());
  }

  @Test
  void propagatesFalseWhenStatusFrameUndelivered() {
    var producer = new ControllableSocketProducer();
    producer.returnFalseForNextSend = true;
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(availableEvent());
    assertFalse(result.success());
    assertThat(result.failureReason()).isEqualTo(NO_ACTIVE_HISTORY_SUBSCRIPTION);
    assertTrue(result.noSessionAvailable());
    assertTrue(
        producer.events.stream()
            .noneMatch(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type"))));
  }

  @Test
  void sendChunk_stampsBinaryFrameWithHistoryJobIdHeader() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    var event = availableEvent();

    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(event);

    assertTrue(result.success());
    var payloadEvent =
        producer.events.stream()
            .filter(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type")))
            .findFirst()
            .orElseThrow();
    assertThat(payloadEvent.headers().get("history-job-id")).isEqualTo(event.jobId().toString());
  }

  @Test
  void sendChunk_rejectsEventWithMissingJobId() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));

    HistoryChunkDeliveryResult result =
        senderWith(producer, gateway).sendChunk(availableEventWithoutJobId());

    assertFalse(result.success());
    assertThat(result.failureReason()).isEqualTo(JOB_ID_MISSING);
    // The frame is rejected before any status or payload frame is dispatched.
    assertTrue(producer.events.isEmpty());
  }

  @Test
  void rejectsMalformedPayloadKey() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    HistoryChunkStatusEvent event =
        new HistoryChunkStatusEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            "chunk-1",
            "opencop:history:chunk-state:test",
            1,
            "history:payload:not-a-uuid:0",
            Instant.parse("2026-04-06T10:00:00Z"),
            Instant.parse("2026-04-06T11:00:00Z"),
            HistoricalResolution.HOUR_1,
            "v1_latest_in_bucket",
            true,
            1,
            HistoryChunkStatus.AVAILABLE_FOR_DELIVERY);

    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(event);
    assertFalse(result.success());
    assertTrue(producer.events.isEmpty());
  }

  @Test
  void sendChunk_rejectsSubjectBearingMalformedPayloadKeyWithStableReasonCode() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    var event = availableEventWithPayloadKey(MALFORMED_SUBJECT_BEARING_PAYLOAD_KEY);

    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(event);

    assertFalse(result.success());
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_KEY_INVALID);
    assertRedactedFailureReason(result.failureReason());
    assertTrue(producer.events.isEmpty());
  }

  @Test
  void sendChunk_returnsStableReasonCodesForPayloadAndTransportFailures() {
    var missingPayloadProducer = new ControllableSocketProducer();
    var missingPayloadEvent = availableEvent();
    var missingPayloadResult =
        senderWith(missingPayloadProducer, fixedGateway(Optional.empty()))
            .sendChunk(missingPayloadEvent);
    assertThat(missingPayloadResult.failureReason()).isEqualTo(PAYLOAD_PART_MISSING);
    assertRedactedFailureReason(missingPayloadResult.failureReason());

    var decodeFailureProducer = new ControllableSocketProducer();
    var decodeFailureEvent = availableEvent();
    var decodeFailureResult =
        senderWith(decodeFailureProducer, fixedGateway(Optional.of(new byte[] {1, 2, 3})))
            .sendChunk(decodeFailureEvent);
    assertThat(decodeFailureResult.failureReason()).isEqualTo(PAYLOAD_DECODE_FAILED);
    assertRedactedFailureReason(decodeFailureResult.failureReason());

    var sendFailureProducer = new ControllableSocketProducer();
    sendFailureProducer.awaitFailure = new IOException("transport failed");
    var sendFailureEvent = availableEvent3Parts();
    var sendFailureResult =
        senderWithThreePartGateway(sendFailureProducer).sendChunk(sendFailureEvent);
    assertThat(sendFailureResult.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    assertRedactedFailureReason(sendFailureResult.failureReason());

    var timeoutProducer = new ControllableSocketProducer();
    timeoutProducer.awaitQueue.add(new CompletableFuture<>());
    var timeoutSender =
        UserHistoryChunkSender.create(
            timeoutProducer,
            objectMapper,
            fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            new SimpleMeterRegistry(),
            mockTracer(),
            100L);
    var timeoutEvent = availableEvent3Parts();
    var timeoutResult = timeoutSender.sendChunk(timeoutEvent);
    assertThat(timeoutResult.failureReason()).isEqualTo(PAYLOAD_SEND_TIMEOUT);
    assertRedactedFailureReason(timeoutResult.failureReason());

    var interruptedProducer = new ControllableSocketProducer();
    interruptedProducer.awaitQueue.add(new CompletableFuture<>());
    var interruptedEvent = availableEvent3Parts();
    Thread.currentThread().interrupt();
    try {
      var interruptedResult =
          senderWithThreePartGateway(interruptedProducer).sendChunk(interruptedEvent);
      assertThat(interruptedResult.failureReason()).isEqualTo(PAYLOAD_SEND_INTERRUPTED);
      assertRedactedFailureReason(interruptedResult.failureReason());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void sendChunk_doesNotCopyAdversarialTransportMessageIntoFailureReason() {
    var producer = new ControllableSocketProducer();
    producer.awaitFailure =
        new IOException("transport failed for " + MALFORMED_SUBJECT_BEARING_PAYLOAD_KEY);

    var result = senderWithThreePartGateway(producer).sendChunk(availableEvent3Parts());

    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    assertRedactedFailureReason(result.failureReason());
  }

  @Test
  void propagatesFalseWhenPayloadFrameUndelivered() {
    var producer = new ControllableSocketProducer();
    producer.returnFalseForProtobufSend = true;
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    HistoryChunkDeliveryResult result = senderWith(producer, gateway).sendChunk(availableEvent());
    assertFalse(result.success());
    assertTrue(result.noSessionAvailable());
    assertThat(result.failureReason()).isEqualTo(NO_ACTIVE_HISTORY_SUBSCRIPTION);
  }

  @Test
  void sendChunk_serializes_parts_by_write_completion() throws Exception {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(new CompletableFuture<>());
    producer.awaitQueue.add(new CompletableFuture<>());
    producer.awaitQueue.add(new CompletableFuture<>());
    CompletableFuture<SendAttemptResult> f1 = producer.awaitQueue.removeFirst();
    CompletableFuture<SendAttemptResult> f2 = producer.awaitQueue.removeFirst();
    CompletableFuture<SendAttemptResult> f3 = producer.awaitQueue.removeFirst();
    producer.awaitQueue.addFirst(f3);
    producer.awaitQueue.addFirst(f2);
    producer.awaitQueue.addFirst(f1);

    var resultHolder = new AtomicReference<HistoryChunkDeliveryResult>();
    var started = new CountDownLatch(1);
    var thread =
        new Thread(
            () -> {
              started.countDown();
              resultHolder.set(
                  senderWithThreePartGateway(producer).sendChunk(availableEvent3Parts()));
            });
    thread.start();
    started.await();

    awaitCondition(() -> producer.awaitCallCount.get() >= 1, Duration.ofSeconds(2), "first await");
    assertThat(producer.awaitCallCount.get()).isEqualTo(1);
    f1.complete(SendAttemptResult.DELIVERED);

    awaitCondition(() -> producer.awaitCallCount.get() >= 2, Duration.ofSeconds(2), "second await");
    assertThat(producer.awaitCallCount.get()).isEqualTo(2);
    f2.complete(SendAttemptResult.DELIVERED);

    awaitCondition(() -> producer.awaitCallCount.get() >= 3, Duration.ofSeconds(2), "third await");
    assertThat(producer.awaitCallCount.get()).isEqualTo(3);
    f3.complete(SendAttemptResult.DELIVERED);

    thread.join(2000);
    assertThat(resultHolder.get().success()).isTrue();
  }

  @Test
  void sendChunk_marks_failed_on_part_await_timeout() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(new CompletableFuture<>());
    var meterRegistry = new SimpleMeterRegistry();
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            meterRegistry,
            mockTracer(),
            100L);

    var result = sender.sendChunk(availableEvent3Parts());
    assertThat(result.success()).isFalse();
    assertThat(result.noSessionAvailable()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_TIMEOUT);
    assertThat(
            meterRegistry
                .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                .gauge()
                .value())
        .isEqualTo(0.0);
  }

  private static Tracer mockTracer() {
    Tracer tracer = org.mockito.Mockito.mock(Tracer.class);
    SpanBuilder spanBuilder = org.mockito.Mockito.mock(SpanBuilder.class);
    Span span = org.mockito.Mockito.mock(Span.class);
    org.mockito.Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any(Context.class)))
        .thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.startSpan()).thenReturn(span);
    org.mockito.Mockito.when(span.makeCurrent()).thenReturn(noopScope());
    return tracer;
  }

  @Test
  void sendChunk_returns_undelivered_when_no_session() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(CompletableFuture.completedFuture(SendAttemptResult.NO_SESSION));
    var result = senderWithThreePartGateway(producer).sendChunk(availableEvent3Parts());
    assertThat(result.noSessionAvailable()).isTrue();
    assertThat(result.failureReason()).isEqualTo(NO_ACTIVE_HISTORY_SUBSCRIPTION);
    assertThat(producer.awaitCallCount.get()).isEqualTo(1);
  }

  @Test
  void sendChunk_emits_status_frames_in_order() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(CompletableFuture.completedFuture(SendAttemptResult.DELIVERED));
    producer.awaitQueue.add(CompletableFuture.completedFuture(SendAttemptResult.DELIVERED));
    producer.awaitQueue.add(CompletableFuture.completedFuture(SendAttemptResult.DELIVERED));

    var result = senderWithThreePartGateway(producer).sendChunk(availableEvent3Parts());
    assertThat(result.success()).isTrue();
    assertThat(producer.callLog)
        .containsExactly(
            "status:SENDING",
            "await:payload",
            "await:payload",
            "await:payload",
            "status:COMPLETED");
  }

  @Test
  void sendChunk_increments_and_decrements_pending_part_gauge() throws Exception {
    var producer = new ControllableSocketProducer();
    var f1 = new CompletableFuture<SendAttemptResult>();
    var f2 = new CompletableFuture<SendAttemptResult>();
    var f3 = new CompletableFuture<SendAttemptResult>();
    producer.awaitQueue.add(f1);
    producer.awaitQueue.add(f2);
    producer.awaitQueue.add(f3);

    var meterRegistry = new SimpleMeterRegistry();
    var resultHolder = new AtomicReference<HistoryChunkDeliveryResult>();
    var started = new CountDownLatch(1);
    var thread =
        new Thread(
            () -> {
              started.countDown();
              resultHolder.set(
                  senderWithGauge(producer, meterRegistry).sendChunk(availableEvent3Parts()));
            });
    thread.start();
    started.await();

    awaitCondition(() -> producer.awaitCallCount.get() >= 1, Duration.ofSeconds(2), "part 1");
    assertThat(
            meterRegistry
                .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                .gauge()
                .value())
        .isEqualTo(1.0);

    f1.complete(SendAttemptResult.DELIVERED);
    awaitCondition(() -> producer.awaitCallCount.get() >= 2, Duration.ofSeconds(2), "part 2");
    awaitCondition(
        () ->
            meterRegistry
                    .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                    .gauge()
                    .value()
                == 1.0,
        Duration.ofSeconds(1),
        "gauge for part 2");

    f2.complete(SendAttemptResult.DELIVERED);
    awaitCondition(() -> producer.awaitCallCount.get() >= 3, Duration.ofSeconds(2), "part 3");
    awaitCondition(
        () ->
            meterRegistry
                    .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                    .gauge()
                    .value()
                == 1.0,
        Duration.ofSeconds(1),
        "gauge for part 3");

    f3.complete(SendAttemptResult.DELIVERED);
    thread.join(2000);
    assertThat(resultHolder.get().success()).isTrue();
    assertThat(
            meterRegistry
                .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                .gauge()
                .value())
        .isEqualTo(0.0);
  }

  @Test
  void sendChunk_decrements_pending_part_gauge_on_exception() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(
        CompletableFuture.<SendAttemptResult>failedFuture(new IOException("transport failed")));
    var meterRegistry = new SimpleMeterRegistry();
    var result = senderWithGauge(producer, meterRegistry).sendChunk(availableEvent3Parts());
    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    assertThat(
            meterRegistry
                .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                .gauge()
                .value())
        .isEqualTo(0.0);
  }

  @Test
  void default_sendToUserAwait_returns_failed_future_with_unsupported_operation() {
    SocketProducer producer =
        new SocketProducer() {
          @Override
          public void sendToUser(UUID userId, String destination, byte[] event, boolean compress) {}

          @Override
          public void broadcast(String topic, byte[] event, boolean compress) {}
        };

    CompletableFuture<SendAttemptResult> future =
        producer.sendToUserAwait(
            UUID.randomUUID(), "/queue/history", new byte[] {1}, Map.of(), false);

    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::join)
        .isInstanceOf(CompletionException.class)
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasRootCauseMessage("sendToUserAwait not implemented");
  }

  @Test
  void sendChunk_reports_root_transport_message_without_wrapper_noise() {
    var producer = new ControllableSocketProducer();
    producer.awaitFailure =
        new CompletionException(
            new java.util.concurrent.ExecutionException(new IOException("transport failed")));
    var meterRegistry = new SimpleMeterRegistry();

    var result = senderWithGauge(producer, meterRegistry).sendChunk(availableEvent3Parts());
    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    assertThat(
            meterRegistry
                .get(UserHistoryChunkSender.PENDING_PART_FUTURES_GAUGE_NAME)
                .gauge()
                .value())
        .isEqualTo(0.0);
  }

  @Test
  void sendChunk_keeps_success_when_completed_status_frame_is_not_delivered() {
    var producer = new ControllableSocketProducer();
    producer.returnFalseForCompletedStatus = true;
    var gateway = fixedGateway(Optional.of(validPayloadBytesForPart(0, 3)));
    var result = senderWith(producer, gateway).sendChunk(availableEvent3Parts());
    assertThat(result.success()).isTrue();
    assertThat(producer.completedStatusAttempts).isEqualTo(1);
  }

  @Test
  void sendChunk_withMalformedTraceContext_doesNotForwardTraceHeaders() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    var tracer = org.mockito.Mockito.mock(Tracer.class);
    var spanBuilder = org.mockito.Mockito.mock(SpanBuilder.class);
    var span = org.mockito.Mockito.mock(Span.class);
    org.mockito.Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any(Context.class)))
        .thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.startSpan()).thenReturn(span);
    org.mockito.Mockito.when(span.makeCurrent()).thenReturn(noopScope());
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            gateway,
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            new SimpleMeterRegistry(),
            tracer,
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
    var event =
        new HistoryChunkStatusEvent(
            availableEvent().targetUserId(),
            availableEvent().requestId(),
            availableEvent().jobId(),
            availableEvent().subjectType(),
            availableEvent().subjectId(),
            availableEvent().chunkKey(),
            availableEvent().chunkStateKey(),
            availableEvent().chunkIndex(),
            availableEvent().payloadKey(),
            availableEvent().chunkFrom(),
            availableEvent().chunkTo(),
            availableEvent().resolution(),
            availableEvent().samplingVersion(),
            availableEvent().cached(),
            availableEvent().partCount(),
            availableEvent().status(),
            Map.of("traceparent", "bad", "x-user-id", "550e8400-e29b-41d4-a716-446655440120"));

    var result = sender.sendChunk(event);
    assertThat(result.success()).isTrue();
    var payloadEvent =
        producer.events.stream()
            .filter(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type")))
            .findFirst()
            .orElseThrow();
    assertThat(payloadEvent.headers()).doesNotContainKeys("traceparent", "tracestate");
    assertThat(producer.statusHistory).containsExactly("SENDING", "COMPLETED");
  }

  @Test
  void sendChunk_withValidTraceContext_forwardsTraceHeaderAndRecordsSpanAndTimer() {
    var producer = new ControllableSocketProducer();
    byte[] payloadBytes = validPayloadBytes();
    var gateway = fixedGateway(Optional.of(payloadBytes));
    var meterRegistry = new SimpleMeterRegistry();
    var tracer = org.mockito.Mockito.mock(Tracer.class);
    var spanBuilder = org.mockito.Mockito.mock(SpanBuilder.class);
    var span = org.mockito.Mockito.mock(Span.class);
    org.mockito.Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any(Context.class)))
        .thenReturn(spanBuilder);
    org.mockito.Mockito.when(spanBuilder.startSpan()).thenReturn(span);
    org.mockito.Mockito.when(span.makeCurrent())
        .thenAnswer(
            ignored ->
                Context.current()
                    .with(
                        Span.wrap(
                            SpanContext.create(
                                "4bf92f3577b34da6a3ce929d0e0e4736",
                                "00f067aa0ba902b7",
                                TraceFlags.getSampled(),
                                TraceState.getDefault())))
                    .makeCurrent());
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            gateway,
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            meterRegistry,
            tracer,
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
    var traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    var event =
        new HistoryChunkStatusEvent(
            availableEvent().targetUserId(),
            availableEvent().requestId(),
            availableEvent().jobId(),
            availableEvent().subjectType(),
            availableEvent().subjectId(),
            availableEvent().chunkKey(),
            availableEvent().chunkStateKey(),
            availableEvent().chunkIndex(),
            availableEvent().payloadKey(),
            availableEvent().chunkFrom(),
            availableEvent().chunkTo(),
            availableEvent().resolution(),
            availableEvent().samplingVersion(),
            availableEvent().cached(),
            availableEvent().partCount(),
            availableEvent().status(),
            Map.of("traceparent", traceparent));

    var result = sender.sendChunk(event);
    assertThat(result.success()).isTrue();
    var payloadEvent =
        producer.events.stream()
            .filter(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type")))
            .findFirst()
            .orElseThrow();
    assertThat(payloadEvent.payload()).isEqualTo(payloadBytes);
    assertThat(payloadEvent.headers().get("traceparent")).isEqualTo(traceparent);
    assertThat(payloadEvent.headers().get("content-type")).isEqualTo("application/x-protobuf");
    assertThat(payloadEvent.headers().get("history-type"))
        .isEqualTo(HistoryEventType.HISTORY_CHUNK_DATA.getName());

    ArgumentCaptor<Context> parentCaptor = ArgumentCaptor.forClass(Context.class);
    org.mockito.Mockito.verify(spanBuilder).setParent(parentCaptor.capture());
    var parentSpanContext = Span.fromContext(parentCaptor.getValue()).getSpanContext();
    assertThat(parentSpanContext.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

    ArgumentCaptor<Attributes> attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    org.mockito.Mockito.verify(span).setAllAttributes(attributesCaptor.capture());
    Attributes attributes = attributesCaptor.getValue();
    assertThat(attributes.get(AttributeKey.stringKey("requestId")))
        .isEqualTo(event.requestId().toString());
    assertThat(attributes.get(AttributeKey.stringKey("jobId"))).isEqualTo(event.jobId().toString());
    assertThat(attributes.get(AttributeKey.stringKey("chunkIndex"))).isEqualTo("1");
    assertThat(attributes.get(AttributeKey.stringKey("subjectType"))).isEqualTo("SOURCE");
    assertThat(attributes.get(AttributeKey.stringKey("samplerVersion")))
        .isEqualTo(event.samplingVersion());
    assertThat(attributes.get(AttributeKey.stringKey("requestedBy"))).isNull();
    assertThat(attributes.get(AttributeKey.stringKey("objectId"))).isNull();

    assertThat(
            meterRegistry
                .find("opencop_history_chunk_delivery_duration_seconds")
                .tag("outcome", "success")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void sendChunk_recordsOutcomeTimersForFailureAndError() {
    var undeliveredProducer = new ControllableSocketProducer();
    undeliveredProducer.returnFalseForNextSend = true;
    var undeliveredRegistry = new SimpleMeterRegistry();
    var undeliveredSender =
        UserHistoryChunkSender.create(
            undeliveredProducer,
            objectMapper,
            fixedGateway(Optional.of(validPayloadBytes())),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            undeliveredRegistry,
            mockTracer(),
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
    var undeliveredResult = undeliveredSender.sendChunk(availableEvent());
    assertThat(undeliveredResult.noSessionAvailable()).isTrue();
    assertThat(undeliveredResult.failureReason()).isEqualTo(NO_ACTIVE_HISTORY_SUBSCRIPTION);
    assertThat(
            undeliveredRegistry
                .find("opencop_history_chunk_delivery_duration_seconds")
                .tag("outcome", "error")
                .timer()
                .count())
        .isEqualTo(1);

    var errorProducer = new ControllableSocketProducer();
    var errorRegistry = new SimpleMeterRegistry();
    var errorSender =
        UserHistoryChunkSender.create(
            errorProducer,
            objectMapper,
            fixedGateway(Optional.empty()),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            errorRegistry,
            mockTracer(),
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
    var errorResult = errorSender.sendChunk(availableEvent());
    assertThat(errorResult.success()).isFalse();
    assertThat(errorResult.noSessionAvailable()).isFalse();
    assertThat(
            errorRegistry
                .find("opencop_history_chunk_delivery_duration_seconds")
                .tag("outcome", "error")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void sendChunk_setsUndeliveredSpanStatus_andEndsSpan_once() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(CompletableFuture.completedFuture(SendAttemptResult.NO_SESSION));
    var span = Mockito.mock(Span.class);
    var sender = senderWithThreePartGatewayAndSpan(producer, span);

    var result = sender.sendChunk(availableEvent3Parts());

    assertThat(result.noSessionAvailable()).isTrue();
    Mockito.verify(span).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "undelivered");
    Mockito.verify(span, Mockito.times(1)).end();
  }

  @Test
  void sendChunk_setsTimeoutSpanStatus_andEndsSpan_once() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(new CompletableFuture<>());
    var span = Mockito.mock(Span.class);
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            new SimpleMeterRegistry(),
            tracerWithSpan(span),
            100L);

    var result = sender.sendChunk(availableEvent3Parts());

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_TIMEOUT);
    Mockito.verify(span).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "timeout");
    Mockito.verify(span).recordException(Mockito.any(TimeoutException.class));
    Mockito.verify(span, Mockito.times(1)).end();
  }

  @Test
  void sendChunk_setsTransportFailureSpanStatus_andEndsSpan_once() {
    var producer = new ControllableSocketProducer();
    producer.awaitQueue.add(
        CompletableFuture.<SendAttemptResult>failedFuture(new IOException("transport failed")));
    var span = Mockito.mock(Span.class);
    var sender = senderWithThreePartGatewayAndSpan(producer, span);

    var result = sender.sendChunk(availableEvent3Parts());

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    Mockito.verify(span).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "transport failed");
    Mockito.verify(span).recordException(Mockito.any(IOException.class));
    Mockito.verify(span, Mockito.times(1)).end();
  }

  @Test
  void sendChunk_setsImmediateSendFailureSpanStatus_andEndsSpan_once() {
    SocketProducer producer =
        new SocketProducer() {
          @Override
          public void sendToUser(UUID userId, String destination, byte[] event, boolean compress) {}

          @Override
          public boolean sendToUser(
              UUID userId,
              String destination,
              byte[] event,
              Map<String, String> headers,
              boolean compress) {
            return true;
          }

          @Override
          public CompletableFuture<SendAttemptResult> sendToUserAwait(
              UUID userId,
              String destination,
              byte[] event,
              Map<String, String> headers,
              boolean compress) {
            throw new IllegalStateException("immediate send failure");
          }

          @Override
          public void broadcast(String topic, byte[] event, boolean compress) {}
        };
    var span = Mockito.mock(Span.class);
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            fixedGateway(Optional.of(validPayloadBytes())),
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            new SimpleMeterRegistry(),
            tracerWithSpan(span),
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);

    var result = sender.sendChunk(availableEvent());

    assertThat(result.success()).isFalse();
    assertThat(result.failureReason()).isEqualTo(PAYLOAD_SEND_FAILED);
    Mockito.verify(span).setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "send-failed");
    Mockito.verify(span).recordException(Mockito.any(IllegalStateException.class));
    Mockito.verify(span, Mockito.times(1)).end();
  }

  @Test
  void sendChunk_binaryHeadersUseCurrentWebsocketSendSpanTraceparent() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    var sender =
        UserHistoryChunkSender.create(
            producer,
            objectMapper,
            gateway,
            partKeyResolver,
            new HistoryChunkDeliveryPrecondition(),
            new SimpleMeterRegistry(),
            tracerWithTraceId("dddddddddddddddddddddddddddddddd"),
            UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
    var event =
        new HistoryChunkStatusEvent(
            availableEvent().targetUserId(),
            availableEvent().requestId(),
            availableEvent().jobId(),
            availableEvent().subjectType(),
            availableEvent().subjectId(),
            availableEvent().chunkKey(),
            availableEvent().chunkStateKey(),
            availableEvent().chunkIndex(),
            availableEvent().payloadKey(),
            availableEvent().chunkFrom(),
            availableEvent().chunkTo(),
            availableEvent().resolution(),
            availableEvent().samplingVersion(),
            availableEvent().cached(),
            availableEvent().partCount(),
            availableEvent().status(),
            Map.of(
                "traceparent",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "tracestate",
                "vendor=one"));

    var result = sender.sendChunk(event);
    assertThat(result.success()).isTrue();
    var payloadEvent =
        producer.events.stream()
            .filter(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type")))
            .findFirst()
            .orElseThrow();
    assertThat(payloadEvent.headers().get("traceparent"))
        .startsWith("00-dddddddddddddddddddddddddddddddd-");
    assertThat(payloadEvent.headers().get("content-type")).isEqualTo("application/x-protobuf");
    assertThat(payloadEvent.headers().get("history-type"))
        .isEqualTo(HistoryEventType.HISTORY_CHUNK_DATA.getName());
  }

  @Test
  void sendChunk_invalidEventTraceContextDoesNotPropagateW3CHeadersToBinaryFrame() {
    var producer = new ControllableSocketProducer();
    var gateway = fixedGateway(Optional.of(validPayloadBytes()));
    var sender = senderWith(producer, gateway);
    var event =
        new HistoryChunkStatusEvent(
            availableEvent().targetUserId(),
            availableEvent().requestId(),
            availableEvent().jobId(),
            availableEvent().subjectType(),
            availableEvent().subjectId(),
            availableEvent().chunkKey(),
            availableEvent().chunkStateKey(),
            availableEvent().chunkIndex(),
            availableEvent().payloadKey(),
            availableEvent().chunkFrom(),
            availableEvent().chunkTo(),
            availableEvent().resolution(),
            availableEvent().samplingVersion(),
            availableEvent().cached(),
            availableEvent().partCount(),
            availableEvent().status(),
            Map.of(
                "traceparent",
                "bad",
                "tracestate",
                "a".repeat(513),
                "x-user-id",
                "550e8400-e29b-41d4-a716-446655440120"));

    var result = sender.sendChunk(event);
    assertThat(result.success()).isTrue();
    var payloadEvent =
        producer.events.stream()
            .filter(
                e ->
                    HistoryEventType.HISTORY_CHUNK_DATA
                        .getName()
                        .equals(e.headers().get("history-type")))
            .findFirst()
            .orElseThrow();
    assertThat(payloadEvent.headers()).doesNotContainKeys("traceparent", "tracestate");
    assertThat(payloadEvent.headers().get("content-type")).isEqualTo("application/x-protobuf");
    assertThat(payloadEvent.headers().get("history-type"))
        .isEqualTo(HistoryEventType.HISTORY_CHUNK_DATA.getName());
  }

  private UserHistoryChunkSender senderWithThreePartGatewayAndSpan(
      SocketProducer producer, Span span) {
    return UserHistoryChunkSender.create(
        producer,
        objectMapper,
        fixedGateway(Optional.of(validPayloadBytesForPart(0, 3))),
        partKeyResolver,
        new HistoryChunkDeliveryPrecondition(),
        new SimpleMeterRegistry(),
        tracerWithSpan(span),
        UserHistoryChunkSender.PART_AWAIT_TIMEOUT_MS);
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

  private static HistoryChunkStatusEvent availableEventWithoutJobId() {
    var event = availableEvent();
    return new HistoryChunkStatusEvent(
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
        event.status());
  }

  private static void assertRedactedFailureReason(String failureReason) {
    assertThat(failureReason)
        .doesNotContain(PAYLOAD_KEY_PREFIX)
        .doesNotContain(PAYLOAD_KEY_FIELD_NAME)
        .doesNotContain(CHUNK_STATE_KEY_FIELD_NAME);
  }

  private static Tracer tracerWithSpan(Span span) {
    Tracer tracer = Mockito.mock(Tracer.class);
    SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class);
    Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.setParent(Mockito.any(Context.class))).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.startSpan()).thenReturn(span);
    Mockito.when(span.makeCurrent()).thenReturn(noopScope());
    return tracer;
  }

  private static Tracer tracerWithTraceId(String traceId) {
    Tracer tracer = Mockito.mock(Tracer.class);
    SpanBuilder spanBuilder = Mockito.mock(SpanBuilder.class);
    Mockito.when(tracer.spanBuilder("history.websocket.send")).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.setParent(Mockito.any(Context.class))).thenReturn(spanBuilder);
    Mockito.when(spanBuilder.startSpan())
        .thenReturn(
            Span.wrap(
                SpanContext.create(
                    traceId,
                    "1111111111111111",
                    TraceFlags.getSampled(),
                    TraceState.getDefault())));
    return tracer;
  }

  private static Scope noopScope() {
    return () -> {};
  }

  private static HistoricalChunkPayloadGateway fixedGateway(Optional<byte[]> response) {
    return new HistoricalChunkPayloadGateway() {
      @Override
      public Optional<byte[]> find(String payloadKey) {
        return response;
      }

      @Override
      public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
        return payloadKeys.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    k -> k, k -> response, (a, b) -> b, java.util.LinkedHashMap::new));
      }
    };
  }

  private static final class ControllableSocketProducer implements SocketProducer {
    final List<SentEvent> events = new ArrayList<>();
    boolean returnFalseForNextSend;
    boolean returnFalseForProtobufSend;
    boolean returnFalseForCompletedStatus;
    Throwable awaitFailure;
    int completedStatusAttempts;
    final AtomicInteger awaitCallCount = new AtomicInteger(0);
    final ArrayDeque<CompletableFuture<SendAttemptResult>> awaitQueue = new ArrayDeque<>();
    final List<String> statusHistory = new ArrayList<>();
    final List<String> callLog = new ArrayList<>();

    @Override
    public void sendToUser(UUID userId, String destination, byte[] event, boolean compress) {
      events.add(new SentEvent(userId, destination, event, Map.of()));
    }

    @Override
    public boolean sendToUser(
        UUID userId,
        String destination,
        byte[] event,
        Map<String, String> headers,
        boolean compress) {
      Map<String, String> safeHeaders = headers != null ? headers : Map.of();
      try {
        var json = new ObjectMapper().readTree(event);
        String status = json.path("status").asText();
        if (!status.isEmpty()) {
          statusHistory.add(status);
          callLog.add("status:" + status);
        }
        if ("COMPLETED".equals(status)) {
          completedStatusAttempts++;
          events.add(new SentEvent(userId, destination, event, safeHeaders));
          if (returnFalseForCompletedStatus) {
            return false;
          }
          return true;
        }
      } catch (Exception ignored) {
      }
      if (returnFalseForNextSend) {
        returnFalseForNextSend = false;
        return false;
      }
      events.add(new SentEvent(userId, destination, event, safeHeaders));
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
      awaitCallCount.incrementAndGet();
      callLog.add("await:payload");
      if (returnFalseForProtobufSend
          && "application/x-protobuf".equals(safeHeaders.get("content-type"))) {
        returnFalseForProtobufSend = false;
        return CompletableFuture.completedFuture(SendAttemptResult.NO_SESSION);
      }
      if (!awaitQueue.isEmpty()) {
        return awaitQueue.removeFirst();
      }
      if (awaitFailure != null) {
        Throwable failure = awaitFailure;
        awaitFailure = null;
        return CompletableFuture.failedFuture(failure);
      }
      events.add(new SentEvent(userId, destination, event, safeHeaders));
      return CompletableFuture.completedFuture(SendAttemptResult.DELIVERED);
    }

    @Override
    public void broadcast(String topic, byte[] event, boolean compress) {}

    record SentEvent(
        UUID userId, String destination, byte[] payload, Map<String, String> headers) {}
  }
}
