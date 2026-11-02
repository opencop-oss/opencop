// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.observability.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HistoricalSubmissionTracing {

  static final String API_SUBMIT_SPAN = "history.api.submit";
  static final String KAFKA_PUBLISH_SPAN = "history.kafka.publish";

  private final Tracer tracer;

  public HistoricalSubmissionTracing(Tracer tracer) {
    this.tracer = tracer;
  }

  public <T> T traceApiSubmit(String subjectType, Supplier<T> operation) {
    Span span = tracer.spanBuilder(API_SUBMIT_SPAN).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      span.setAllAttributes(
          HistoricalTraceContext.spanAttributes(apiSubmitAttributes(subjectType)));
      T result = operation.get();
      span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
      return result;
    } catch (RuntimeException e) {
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }

  public void traceKafkaPublish(HistoricalTracksJobSubmission submission, Runnable operation) {
    Span span = tracer.spanBuilder(KAFKA_PUBLISH_SPAN).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      span.setAllAttributes(
          HistoricalTraceContext.spanAttributes(kafkaPublishAttributes(submission)));
      operation.run();
      span.setStatus(StatusCode.OK, Outcome.SUCCESS.value());
    } catch (RuntimeException e) {
      span.setStatus(StatusCode.ERROR, Outcome.ERROR.value());
      span.recordException(e);
      throw e;
    } finally {
      span.end();
    }
  }

  private Map<String, String> apiSubmitAttributes(String subjectType) {
    Map<String, String> attributes = new LinkedHashMap<>();
    if (subjectType != null) {
      attributes.put(HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, subjectType);
    }
    return attributes;
  }

  private Map<String, String> kafkaPublishAttributes(HistoricalTracksJobSubmission submission) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(HistoricalTraceContext.ATTRIBUTE_REQUEST_ID, submission.requestId().toString());
    attributes.put(HistoricalTraceContext.ATTRIBUTE_JOB_ID, submission.jobId().toString());
    attributes.put(
        HistoricalTraceContext.ATTRIBUTE_SUBJECT_TYPE, aggregatedSubjectTypes(submission));
    return attributes;
  }

  private static String aggregatedSubjectTypes(HistoricalTracksJobSubmission submission) {
    return submission.subjects().stream()
        .map(subject -> subject.subjectType().name())
        .distinct()
        .collect(Collectors.joining(","));
  }
}
