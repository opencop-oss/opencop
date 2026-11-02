// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobService;
import com.opencop.processor.technical.spring.config.HistoricalExecutionConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class HistoricalTracksConsumer {
  static final String PROCESSOR_CONSUME_SPAN = "history.processor.consume";
  private static final String CONSUMER_ID = "historicalTracksConsumer";
  private static final String HISTORY_TOPIC_PROPERTY =
      "${opencop.messaging-broker.listeners.history.topic}";
  private static final String HISTORY_CONTAINER_FACTORY = "historyFactory";

  private static final Logger log = LoggerFactory.getLogger(HistoricalTracksConsumer.class);

  private final HistoricalJobService ingressService;
  private final ObjectMapper objectMapper;
  private final Tracer tracer;

  public HistoricalTracksConsumer(
      HistoricalJobService ingressService,
      ObjectMapper objectMapper,
      @Qualifier(HistoricalExecutionConfig.PROCESSOR_CONSUMER_TRACER_BEAN) Tracer tracer) {
    this.ingressService = ingressService;
    this.objectMapper = objectMapper;
    this.tracer = tracer;
  }

  @KafkaListener(
      id = CONSUMER_ID,
      topics = HISTORY_TOPIC_PROPERTY,
      containerFactory = HISTORY_CONTAINER_FACTORY)
  public void consume(ConsumerRecord<String, byte[]> record) {
    byte[] payload = record.value();
    Span span =
        tracer
            .spanBuilder(PROCESSOR_CONSUME_SPAN)
            .setParent(HistoricalTraceContext.parentContext(traceHeaders(record)))
            .startSpan();
    try {
      try (Scope ignored = span.makeCurrent()) {
        JobSubmission job = objectMapper.readValue(payload, JobSubmission.class);
        if (!(job instanceof HistoricalTracksJobSubmission historicalJob)) {
          throw new IllegalStateException(
              "Unsupported job submission for historical consumer: " + job.getClass().getName());
        }
        if (historicalJob.subjects() == null || historicalJob.subjects().isEmpty()) {
          throw new IllegalStateException("Historical payload must include at least one subject");
        }
        span.setAllAttributes(HistoricalTraceContext.spanAttributes(spanAttributes(historicalJob)));

        log.info(
            "Submitting historical job {} of type {} initiated by {} from payload ({} bytes)",
            historicalJob.jobId(),
            historicalJob.getClass().getSimpleName(),
            historicalJob.issuer(),
            payload.length);
        ingressService.accept(historicalJob);
        span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
      }
    } catch (Exception e) {
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(e);
      log.error("Failed to dispatch historical job from payload ({} bytes)", payload.length, e);
      throw new IllegalStateException("Failed to dispatch historical job", e);
    } finally {
      span.end();
    }
  }

  private static Map<String, String> traceHeaders(ConsumerRecord<String, byte[]> record) {
    Map<String, String> headers = new LinkedHashMap<>();
    String traceparent = headerValue(record, HistoricalTraceContext.TRACEPARENT);
    if (traceparent != null) {
      headers.put(HistoricalTraceContext.TRACEPARENT, traceparent);
    }
    String tracestate = headerValue(record, HistoricalTraceContext.TRACESTATE);
    if (tracestate != null) {
      headers.put(HistoricalTraceContext.TRACESTATE, tracestate);
    }
    return headers;
  }

  private static String headerValue(ConsumerRecord<String, byte[]> record, String key) {
    var header = record.headers().lastHeader(key);
    if (header == null || header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private static Map<String, String> spanAttributes(HistoricalTracksJobSubmission historicalJob) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, historicalJob.requestId().toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, historicalJob.jobId().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, aggregatedSubjectTypes(historicalJob));
    return attributes;
  }

  private static String aggregatedSubjectTypes(HistoricalTracksJobSubmission historicalJob) {
    return historicalJob.subjects().stream()
        .map(subject -> subject.subjectType().name())
        .distinct()
        .collect(Collectors.joining(","));
  }
}
