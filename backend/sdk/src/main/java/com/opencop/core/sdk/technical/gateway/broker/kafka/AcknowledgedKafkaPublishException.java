// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker.kafka;

import java.io.Serializable;
import java.util.regex.Pattern;

public final class AcknowledgedKafkaPublishException extends RuntimeException {

  private static final int MAX_REPRESENTATIVE_EXCEPTION_MESSAGE_LENGTH = 512;
  private static final String TRUNCATED_SUFFIX = "...[truncated]";
  private static final Pattern SENSITIVE_DETAIL_PATTERN =
      Pattern.compile(
          "(?i)(authorization|cookie|password|passwd|secret|token|api[-_]?key|access[-_]?key)"
              + "\\s*[:=]\\s*[^\\s,;]+");

  private final String topic;
  private final FailureSummary summary;
  private final String representativeExceptionClass;
  private final String representativeExceptionMessage;

  AcknowledgedKafkaPublishException(
      String topic, FailureSummary summary, Throwable representativeException) {
    super(message(topic, summary, representativeException), representativeException);
    this.topic = topic;
    this.summary = summary;
    this.representativeExceptionClass = representativeExceptionClass(representativeException);
    this.representativeExceptionMessage = representativeExceptionMessage(representativeException);
  }

  public String topic() {
    return topic;
  }

  public int batchSize() {
    return summary.batchSize();
  }

  public int acknowledgedCount() {
    return summary.acknowledgedCount();
  }

  public int timeoutCount() {
    return summary.timeoutCount();
  }

  public int resettableFailureCount() {
    return summary.resettableFailureCount();
  }

  public int nonResettableFailureCount() {
    return summary.nonResettableFailureCount();
  }

  public boolean resetAttempted() {
    return summary.resetAttempted();
  }

  public String representativeExceptionClass() {
    return representativeExceptionClass;
  }

  public String representativeExceptionMessage() {
    return representativeExceptionMessage;
  }

  private static String representativeExceptionClass(Throwable representativeException) {
    return representativeException == null
        ? null
        : representativeException.getClass().getSimpleName();
  }

  private static String representativeExceptionMessage(Throwable representativeException) {
    if (representativeException == null) {
      return null;
    }
    var message = representativeException.getMessage();
    var diagnosticMessage =
        message == null || message.isBlank()
            ? representativeException.getClass().getSimpleName()
            : message;
    return capped(SENSITIVE_DETAIL_PATTERN.matcher(diagnosticMessage).replaceAll("$1=[REDACTED]"));
  }

  private static String capped(String message) {
    if (message.length() <= MAX_REPRESENTATIVE_EXCEPTION_MESSAGE_LENGTH) {
      return message;
    }
    return message.substring(
            0, MAX_REPRESENTATIVE_EXCEPTION_MESSAGE_LENGTH - TRUNCATED_SUFFIX.length())
        + TRUNCATED_SUFFIX;
  }

  record FailureSummary(
      int batchSize,
      int acknowledgedCount,
      int timeoutCount,
      int resettableFailureCount,
      int nonResettableFailureCount,
      boolean resetAttempted)
      implements Serializable {}

  private static String message(
      String topic, FailureSummary summary, Throwable representativeException) {
    return ("Acknowledged Kafka publish failed: topic=%s, batchSize=%d, acknowledgedCount=%d,"
            + " timeoutCount=%d, resettableFailureCount=%d, nonResettableFailureCount=%d,"
            + " resetAttempted=%s, representativeExceptionClass=%s,"
            + " representativeExceptionMessage=%s")
        .formatted(
            topic,
            summary.batchSize(),
            summary.acknowledgedCount(),
            summary.timeoutCount(),
            summary.resettableFailureCount(),
            summary.nonResettableFailureCount(),
            summary.resetAttempted(),
            representativeExceptionClass(representativeException),
            representativeExceptionMessage(representativeException));
  }
}
