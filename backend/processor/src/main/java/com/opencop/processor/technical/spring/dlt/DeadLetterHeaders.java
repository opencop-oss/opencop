// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_STACKTRACE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_KEY_EXCEPTION_STACKTRACE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.InvalidMappingDiagnosticException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.SingleRecordHeader;

public final class DeadLetterHeaders {

  public static final String STAGE = "opencop-dlt-stage";
  public static final String FAILURE_KIND = "opencop-dlt-failure-kind";
  public static final String DIAGNOSTIC_CATEGORY = "opencop-dlt-diagnostic-category";
  public static final String DIAGNOSTIC_FIELD = "opencop-dlt-diagnostic-field";

  private static final String RAW_TOPIC = "opencop.event.external.raw";
  private static final String MAPPED_TOPIC = "opencop.geo.mapped";
  private static final String VALIDATED_TOPIC = "opencop.geo.validated";
  private static final String NOTIFICATION_TOPIC = "opencop.notification";
  private static final String JOBS_TOPIC = "opencop.jobs";
  private static final String HISTORY_TOPIC = "opencop.history.jobs";
  private static final String BYOD_TOPIC_PREFIX = "opencop.byod.";
  private static final String UNKNOWN_STAGE = "UNKNOWN";
  private static final Pattern SENSITIVE_DETAIL_PATTERN =
      Pattern.compile(
          "(?i)(authorization|cookie|password|passwd|secret|token|api[-_]?key|access[-_]?key)"
              + "\\s*[:=]\\s*[^\\s,;]+");

  private DeadLetterHeaders() {}

  public static RecordHeaders forSpringRecoverer(
      ConsumerRecord<?, ?> sourceRecord, Exception failure) {
    return forSpringRecoverer(sourceRecord, failure, stageForTopic(sourceRecord.topic()));
  }

  public static RecordHeaders forSpringRecoverer(
      ConsumerRecord<?, ?> sourceRecord, Exception failure, Stage stage) {
    return createHeaders(sourceRecord, failure, stage, DeadLetterContext.fromHeaders(sourceRecord));
  }

  public static RecordHeaders forByodRecoverer(
      ConsumerRecord<?, ?> sourceRecord, Exception failure) {
    return createHeaders(
        sourceRecord, failure, Stage.BYOD, DeadLetterContext.fromHeaders(sourceRecord));
  }

  public static RecordHeaders forManualRaw(ConsumerRecord<?, ?> sourceRecord, Throwable failure) {
    return forManual(sourceRecord, failure, Stage.RAW);
  }

  public static RecordHeaders forManualPersistence(
      ConsumerRecord<?, ?> sourceRecord, Throwable failure) {
    return forManual(sourceRecord, failure, Stage.PERSIST);
  }

  public static RecordHeaders forManualPersistence(
      ConsumerRecord<?, ?> sourceRecord, Throwable failure, DeadLetterContext context) {
    return forManual(sourceRecord, failure, Stage.PERSIST, context);
  }

  private static RecordHeaders forManual(
      ConsumerRecord<?, ?> sourceRecord, Throwable failure, Stage stage) {
    return forManual(sourceRecord, failure, stage, null);
  }

  private static RecordHeaders forManual(
      ConsumerRecord<?, ?> sourceRecord,
      Throwable failure,
      Stage stage,
      DeadLetterContext context) {
    var mergedContext = DeadLetterContext.fromHeaders(sourceRecord).merge(context);
    return createHeaders(sourceRecord, failure, stage, mergedContext);
  }

  private static RecordHeaders createHeaders(
      ConsumerRecord<?, ?> sourceRecord,
      Throwable failure,
      Stage stage,
      DeadLetterContext context) {
    var headers = new RecordHeaders();
    removeManagedHeaders(headers);
    Throwable root = DeadLetterFailureClassifier.rootCause(failure);
    addHeader(headers, DLT_EXCEPTION_FQCN, root.getClass().getName());
    addHeader(headers, DLT_EXCEPTION_MESSAGE, sanitizedMessage(root));
    addHeader(headers, DLT_ORIGINAL_TOPIC, sourceRecord.topic());
    addHeader(headers, DLT_ORIGINAL_PARTITION, String.valueOf(sourceRecord.partition()));
    addHeader(headers, DLT_ORIGINAL_OFFSET, String.valueOf(sourceRecord.offset()));
    addHeader(headers, STAGE, stage.name());
    addHeader(headers, FAILURE_KIND, DeadLetterFailureClassifier.classify(failure).name());
    addDiagnosticHeaders(headers, failure);
    addContextHeader(headers, context);
    return headers;
  }

  private static void removeManagedHeaders(RecordHeaders headers) {
    headers.remove(DLT_EXCEPTION_FQCN);
    headers.remove(DLT_EXCEPTION_MESSAGE);
    headers.remove(DLT_EXCEPTION_STACKTRACE);
    headers.remove(DLT_KEY_EXCEPTION_STACKTRACE);
    headers.remove(DLT_ORIGINAL_TOPIC);
    headers.remove(DLT_ORIGINAL_PARTITION);
    headers.remove(DLT_ORIGINAL_OFFSET);
    headers.remove(STAGE);
    headers.remove(FAILURE_KIND);
    headers.remove(DIAGNOSTIC_CATEGORY);
    headers.remove(DIAGNOSTIC_FIELD);
    headers.remove(DeadLetterFailureIndexer.CONTEXT_HEADER);
  }

  private static void addHeader(RecordHeaders headers, String headerName, String value) {
    headers.add(new SingleRecordHeader(headerName, value.getBytes(StandardCharsets.UTF_8)));
  }

  private static void addContextHeader(RecordHeaders headers, DeadLetterContext context) {
    if (context == null || context.isEmpty()) {
      return;
    }
    addHeader(headers, DeadLetterFailureIndexer.CONTEXT_HEADER, context.toHeaderJson());
  }

  private static void addDiagnosticHeaders(RecordHeaders headers, Throwable failure) {
    var diagnostic = findInvalidMappingDiagnostic(failure);
    if (diagnostic == null) {
      return;
    }
    addHeader(headers, DIAGNOSTIC_CATEGORY, "INVALID_MAPPING");
    if (diagnostic.diagnosticField() != null) {
      addHeader(headers, DIAGNOSTIC_FIELD, diagnostic.diagnosticField());
    }
  }

  private static InvalidMappingDiagnosticException findInvalidMappingDiagnostic(Throwable failure) {
    var current = failure;
    while (current != null) {
      if (current instanceof InvalidMappingDiagnosticException diagnostic) {
        return diagnostic;
      }
      current = current.getCause();
    }
    return null;
  }

  private static Stage stageForTopic(String topic) {
    if (RAW_TOPIC.equals(topic)) {
      return Stage.RAW;
    }
    if (MAPPED_TOPIC.equals(topic)) {
      return Stage.VALIDATION;
    }
    if (VALIDATED_TOPIC.equals(topic)) {
      return Stage.REAL_TIME;
    }
    if (NOTIFICATION_TOPIC.equals(topic)) {
      return Stage.NOTIFICATION;
    }
    if (JOBS_TOPIC.equals(topic)) {
      return Stage.JOBS;
    }
    if (HISTORY_TOPIC.equals(topic)) {
      return Stage.HISTORY;
    }
    if (topic != null && topic.startsWith(BYOD_TOPIC_PREFIX)) {
      return Stage.BYOD;
    }
    return Stage.valueOf(UNKNOWN_STAGE);
  }

  private static String sanitizedMessage(Throwable failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return failure.getClass().getSimpleName();
    }
    return SENSITIVE_DETAIL_PATTERN.matcher(message).replaceAll("$1=[REDACTED]");
  }

  public enum Stage {
    RAW,
    VALIDATION,
    REAL_TIME,
    WATCHBOX,
    PERSIST,
    NOTIFICATION,
    JOBS,
    HISTORY,
    BYOD,
    UNKNOWN
  }

  public enum FailureKind {
    CONTROL_UNSUPPORTED,
    MALFORMED,
    MAPPING_ERROR,
    INFRA,
    PROCESSING
  }
}
