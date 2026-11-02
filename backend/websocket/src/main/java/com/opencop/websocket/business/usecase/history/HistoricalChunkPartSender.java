// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import com.opencop.core.sdk.model.history.HistoryEventType;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.SpanError;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import opencop.core.proto.history.HistoricalChunkProtos.HistoricalChunkPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends a single binary protobuf chunk part to a user, awaits the websocket write-completion
 * callback under a timeout, owns the OpenTelemetry span lifecycle and the Micrometer pending-future
 * gauge. Returns {@code false} when the user has no active subscribed session; throws {@link
 * HistoryChunkDeliveryException} with a typed reason otherwise.
 */
public class HistoricalChunkPartSender {

  private static final Logger log = LoggerFactory.getLogger(HistoricalChunkPartSender.class);
  private static final String DESTINATION = "/user/queue/history";
  private static final String DELIVERY_SPAN_NAME = "history.websocket.send";
  private static final String HEADER_CONTENT_TYPE = "content-type";
  private static final String HEADER_HISTORY_TYPE = "history-type";
  private static final String HEADER_HISTORY_JOB_ID = "history-job-id";
  private static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";

  private final SocketProducer producer;
  private final Tracer tracer;
  private final long partAwaitTimeoutMs;
  private final AtomicInteger pendingPartFutures = new AtomicInteger(0);

  public HistoricalChunkPartSender(
      SocketProducer producer,
      Tracer tracer,
      MeterRegistry meterRegistry,
      long partAwaitTimeoutMs,
      String pendingPartFuturesGaugeName) {
    this.producer = producer;
    this.tracer = tracer;
    this.partAwaitTimeoutMs = partAwaitTimeoutMs;
    Gauge.builder(pendingPartFuturesGaugeName, pendingPartFutures, AtomicInteger::get)
        .register(meterRegistry);
  }

  /**
   * @return {@code true} if the part was delivered to an active session; {@code false} if the user
   *     has no local subscribed session and the frame was not received.
   * @throws HistoryChunkDeliveryException on encode failure, transport failure, timeout, or
   *     interruption — the {@link HistoryChunkDeliveryException#reason()} carries the typed reason.
   */
  public boolean send(HistoryChunkStatusEvent availableEvent, byte[] payloadBytes) {
    decodeOrFail(availableEvent, payloadBytes);

    Span span =
        tracer.spanBuilder(DELIVERY_SPAN_NAME).setParent(parentContext(availableEvent)).startSpan();
    span.setAllAttributes(spanAttributes(availableEvent));
    try {
      Map<String, String> headers = headersWithCurrentTraceContext(availableEvent, span);
      pendingPartFutures.incrementAndGet();
      CompletableFuture<SendAttemptResult> rawFuture =
          submitOrFail(availableEvent, span, payloadBytes, headers);
      CompletableFuture<SendAttemptResult> timedFuture =
          rawFuture
              .orTimeout(partAwaitTimeoutMs, TimeUnit.MILLISECONDS)
              .whenComplete((r, ex) -> pendingPartFutures.decrementAndGet());
      return awaitCompletion(availableEvent, span, timedFuture);
    } finally {
      span.end();
    }
  }

  private static void decodeOrFail(HistoryChunkStatusEvent event, byte[] payloadBytes) {
    // Defensive guard: we only ever forward bytes the producer-side committed as a complete
    // HistoricalChunkPayload, but a malformed cache entry would corrupt every downstream consumer.
    // Decoding here trades a small CPU cost for an early, typed failure.
    try {
      HistoricalChunkPayload.parseFrom(payloadBytes);
    } catch (Exception e) {
      log.warn(
          "Historical websocket payload decode failed for job {} chunkIndex {} reason={}",
          event.jobId(),
          event.chunkIndex(),
          HistoryChunkDeliveryFailureReason.PAYLOAD_DECODE_FAILED);
      throw new HistoryChunkDeliveryException(
          HistoryChunkDeliveryFailureReason.PAYLOAD_DECODE_FAILED, e);
    }
  }

  private CompletableFuture<SendAttemptResult> submitOrFail(
      HistoryChunkStatusEvent event, Span span, byte[] payloadBytes, Map<String, String> headers) {
    try {
      return producer.sendToUserAwait(
          event.targetUserId(), DESTINATION, payloadBytes, headers, false);
    } catch (Exception e) {
      pendingPartFutures.decrementAndGet();
      log.warn(
          "Historical websocket binary delivery failed for job {} chunkIndex {} reason={}",
          event.jobId(),
          event.chunkIndex(),
          HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_FAILED);
      failSpan(span, SpanError.SEND_FAILED.description(), e);
      throw new HistoryChunkDeliveryException(
          HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_FAILED, e);
    }
  }

  private boolean awaitCompletion(
      HistoryChunkStatusEvent event, Span span, CompletableFuture<SendAttemptResult> timedFuture) {
    try {
      SendAttemptResult result = timedFuture.get();
      if (result == SendAttemptResult.NO_SESSION) {
        span.setStatus(StatusCode.ERROR, Outcome.UNDELIVERED.value());
        return false;
      }
      span.setStatus(StatusCode.OK);
      return true;
    } catch (ExecutionException e) {
      throw mapAwaitFailure(event, span, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failSpan(span, SpanError.INTERRUPTED.description(), e);
      throw new HistoryChunkDeliveryException(
          HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_INTERRUPTED, e);
    }
  }

  private static HistoryChunkDeliveryException mapAwaitFailure(
      HistoryChunkStatusEvent event, Span span, ExecutionException e) {
    Throwable cause = e.getCause();
    if (cause instanceof TimeoutException) {
      failSpan(span, SpanError.TIMEOUT.description(), cause);
      return new HistoryChunkDeliveryException(
          HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_TIMEOUT, cause);
    }
    Throwable rootCause = cause;
    while (rootCause instanceof CompletionException || rootCause instanceof ExecutionException) {
      Throwable next = rootCause.getCause();
      if (next == null) {
        break;
      }
      rootCause = next;
    }
    String rootMessage =
        rootCause.getMessage() != null ? rootCause.getMessage() : rootCause.toString();
    log.warn(
        "Historical websocket binary delivery failed for job {} chunkIndex {} reason={}",
        event.jobId(),
        event.chunkIndex(),
        HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_FAILED);
    failSpan(span, rootMessage, rootCause);
    return new HistoryChunkDeliveryException(
        HistoryChunkDeliveryFailureReason.PAYLOAD_SEND_FAILED, rootCause);
  }

  private static void failSpan(Span span, String message, Throwable cause) {
    span.setStatus(StatusCode.ERROR, message);
    span.recordException(cause);
  }

  private static Map<String, String> headersWithCurrentTraceContext(
      HistoryChunkStatusEvent event, Span span) {
    boolean hasValidParentTraceparent =
        HistoricalTraceContext.sanitize(event.traceContext())
            .containsKey(HistoricalTraceContext.TRACEPARENT);
    try (Scope ignored = span.makeCurrent()) {
      Map<String, String> traceContext =
          hasValidParentTraceparent ? HistoricalTraceContext.captureCurrent() : Map.of();
      return buildBinaryHeaders(event, traceContext);
    }
  }

  /**
   * Per-delivery binary frame headers. The job id is stamped here, not baked into the
   * content-addressed chunk payload, because the same cached bytes are reused across jobs and users
   * — see the class-level payload-key documentation. Stamping per send keeps the cache safe while
   * letting each listener accept only the chunks for its own job. {@code jobId} is guaranteed
   * non-null by {@link HistoryChunkDeliveryPrecondition}, so it is stamped unconditionally — a
   * missing id is a delivery failure there, not a silent omission here.
   */
  private static Map<String, String> buildBinaryHeaders(
      HistoryChunkStatusEvent event, Map<String, String> eventTraceContext) {
    Map<String, String> merged = new LinkedHashMap<>();
    merged.put(HEADER_CONTENT_TYPE, PROTOBUF_CONTENT_TYPE);
    merged.put(HEADER_HISTORY_TYPE, HistoryEventType.HISTORY_CHUNK_DATA.getName());
    merged.put(HEADER_HISTORY_JOB_ID, event.jobId().toString());
    merged.putAll(HistoricalTraceContext.sanitize(eventTraceContext));
    return Map.copyOf(merged);
  }

  private static Context parentContext(HistoryChunkStatusEvent event) {
    return HistoricalTraceContext.parentContext(event.traceContext());
  }

  private static Attributes spanAttributes(HistoryChunkStatusEvent event) {
    Map<String, String> attributes = new LinkedHashMap<>();
    if (event.requestId() != null) {
      attributes.put(HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, event.requestId().toString());
    }
    if (event.jobId() != null) {
      attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, event.jobId().toString());
    }
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_CHUNK_INDEX, Integer.toString(event.chunkIndex()));
    if (event.subjectType() != null) {
      attributes.put(HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, event.subjectType().name());
    }
    if (event.samplingVersion() != null) {
      attributes.put(HistoricalTraceContext.ATTRIBUTE_SAMPLER_VERSION, event.samplingVersion());
    }
    return HistoricalTraceContext.spanAttributes(attributes);
  }
}
