// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoryChunkStatus;
import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.websocket.ports.gateway.history.HistoricalChunkPayloadGateway;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Tracer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class UserHistoryChunkSender {

  private static final Logger log = LoggerFactory.getLogger(UserHistoryChunkSender.class);

  /**
   * Per-part write-completion await timeout in milliseconds. Derived as three times the session
   * send-time limit so a slow consumer can stall briefly without tripping the timeout. If the
   * session send-time limit changes, update this value (or move both to {@code
   * WebsocketExecutionProperties} so the derivation is enforced).
   */
  public static final long PART_AWAIT_TIMEOUT_MS = 30_000L;

  /** Micrometer gauge name for the count of in-flight part send futures. */
  static final String PENDING_PART_FUTURES_GAUGE_NAME =
      "opencop.websocket.history.pending_part_futures";

  private static final String DESTINATION = "/user/queue/history";
  private static final String CHUNK_DELIVERY_TIMER_NAME =
      "opencop_history_chunk_delivery_duration_seconds";
  private static final String TAG_OUTCOME = "outcome";

  private final SocketProducer producer;
  private final ObjectMapper mapper;
  private final HistoricalChunkPayloadGateway payloadGateway;
  private final HistoricalChunkPartKeyResolver partKeyResolver;
  private final HistoryChunkDeliveryPrecondition precondition;
  private final HistoricalChunkPartSender partSender;
  private final MeterRegistry meterRegistry;

  public UserHistoryChunkSender(
      SocketProducer producer,
      ObjectMapper mapper,
      HistoricalChunkPayloadGateway payloadGateway,
      HistoricalChunkPartKeyResolver partKeyResolver,
      HistoryChunkDeliveryPrecondition precondition,
      HistoricalChunkPartSender partSender,
      MeterRegistry meterRegistry) {
    this.producer = producer;
    this.mapper = mapper;
    this.payloadGateway = payloadGateway;
    this.partKeyResolver = partKeyResolver;
    this.precondition = precondition;
    this.partSender = partSender;
    this.meterRegistry = meterRegistry;
  }

  /** Convenience factory used by Spring config and tests that want the default collaborators. */
  public static UserHistoryChunkSender create(
      SocketProducer producer,
      ObjectMapper mapper,
      HistoricalChunkPayloadGateway payloadGateway,
      HistoricalChunkPartKeyResolver partKeyResolver,
      HistoryChunkDeliveryPrecondition precondition,
      MeterRegistry meterRegistry,
      Tracer tracer,
      long partAwaitTimeoutMs) {
    HistoricalChunkPartSender partSender =
        new HistoricalChunkPartSender(
            producer, tracer, meterRegistry, partAwaitTimeoutMs, PENDING_PART_FUTURES_GAUGE_NAME);
    return new UserHistoryChunkSender(
        producer, mapper, payloadGateway, partKeyResolver, precondition, partSender, meterRegistry);
  }

  public HistoryChunkDeliveryResult sendChunk(HistoryChunkStatusEvent availableEvent) {
    long startedNanos = System.nanoTime();
    Outcome outcome = Outcome.ERROR;
    try {
      HistoryChunkDeliveryResult result = attemptDelivery(availableEvent);
      if (result.success()) {
        outcome = Outcome.SUCCESS;
      }
      return result;
    } finally {
      recordDeliveryDuration(startedNanos, outcome);
    }
  }

  private HistoryChunkDeliveryResult attemptDelivery(HistoryChunkStatusEvent availableEvent) {
    HistoryChunkDeliveryPreconditionResult preconditionResult =
        precondition.validate(availableEvent);
    if (!preconditionResult.valid()) {
      HistoryChunkDeliveryFailureReason failureReason =
          preconditionResult.failureReason().orElseThrow();
      log.warn(
          "Rejecting history chunk send for job {} chunkIndex {} reason={}",
          availableEvent.jobId(),
          availableEvent.chunkIndex(),
          failureReason);
      return HistoryChunkDeliveryResult.failedResult(failureReason);
    }

    List<String> payloadKeys = payloadKeys(availableEvent.payloadKey(), availableEvent.partCount());
    // All-or-nothing fetch: partial availability would commit some parts to the user and leave
    // the rest missing — fail fast instead so the upstream replay path can retry as a whole.
    Map<String, Optional<byte[]>> payloads = payloadGateway.findAll(payloadKeys);
    if (payloads.values().stream().anyMatch(Optional::isEmpty)) {
      log.warn(
          "Skipping history chunk send for job {} chunkIndex {} reason={}",
          availableEvent.jobId(),
          availableEvent.chunkIndex(),
          HistoryChunkDeliveryFailureReason.PAYLOAD_PART_MISSING);
      return HistoryChunkDeliveryResult.failedResult(
          HistoryChunkDeliveryFailureReason.PAYLOAD_PART_MISSING);
    }

    // Send the SENDING status frame; if the user has no active subscribed session the chunk
    // cannot be delivered — report a terminal failure without sending payload parts.
    if (!sendStatusFrame(statusEvent(availableEvent, HistoryChunkStatus.SENDING))) {
      return HistoryChunkDeliveryResult.noActiveHistorySessionResult();
    }

    try {
      for (int partIndex = 0; partIndex < availableEvent.partCount(); partIndex++) {
        byte[] payloadBytes = payloads.get(payloadKeys.get(partIndex)).orElseThrow();
        if (!partSender.send(availableEvent, payloadBytes)) {
          return HistoryChunkDeliveryResult.noActiveHistorySessionResult();
        }
      }
    } catch (HistoryChunkDeliveryException e) {
      log.warn(
          "Historical websocket chunk send failed for job {} chunkIndex {} reason={}",
          availableEvent.jobId(),
          availableEvent.chunkIndex(),
          e.reason());
      return HistoryChunkDeliveryResult.failedResult(e.reason());
    }
    sendStatusFrame(statusEvent(availableEvent, HistoryChunkStatus.COMPLETED));
    return HistoryChunkDeliveryResult.deliveredResult();
  }

  /**
   * Sends a JSON status frame to the user. Returns {@code false} when the user has no local
   * subscribed session and the frame was not received.
   */
  private boolean sendStatusFrame(HistoryChunkStatusEvent event) {
    return producer.sendToUser(
        event.targetUserId(), DESTINATION, mapper.writeValueAsBytes(event), null, false);
  }

  private List<String> payloadKeys(String basePayloadKey, int partCount) {
    List<String> keys = new ArrayList<>(partCount);
    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      keys.add(partKeyResolver.payloadPartKey(basePayloadKey, partIndex));
    }
    return keys;
  }

  private static HistoryChunkStatusEvent statusEvent(
      HistoryChunkStatusEvent source, HistoryChunkStatus status) {
    return new HistoryChunkStatusEvent(
        source.targetUserId(),
        source.requestId(),
        source.jobId(),
        source.subjectType(),
        source.subjectId(),
        source.chunkKey(),
        source.chunkStateKey(),
        source.chunkIndex(),
        source.payloadKey(),
        source.chunkFrom(),
        source.chunkTo(),
        source.resolution(),
        source.samplingVersion(),
        source.cached(),
        source.partCount(),
        status);
  }

  private void recordDeliveryDuration(long startedNanos, Outcome outcome) {
    Timer.builder(CHUNK_DELIVERY_TIMER_NAME)
        .tag(TAG_OUTCOME, outcome.value())
        .register(meterRegistry)
        .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
  }
}
