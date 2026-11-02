// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.core.sdk.technical.model.serializer.kafka.OpencopKafkaHeaders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;

public record DeadLetterContext(UUID sourceId, UUID jobId, UUID mapperId, UUID importId) {

  public static DeadLetterContext fromHeaders(ConsumerRecord<?, ?> sourceRecord) {
    return fromHeaders(sourceRecord.headers());
  }

  static DeadLetterContext fromHeaders(Headers headers) {
    return new DeadLetterContext(
        uuidHeader(headers, OpencopKafkaHeaders.SOURCE_ID.getName()),
        null,
        uuidHeader(headers, OpencopKafkaHeaders.MAPPER_ID.getName()),
        uuidHeader(headers, OpencopKafkaHeaders.IMPORT_ID.getName()));
  }

  public DeadLetterContext merge(DeadLetterContext override) {
    if (override == null) {
      return this;
    }
    return new DeadLetterContext(
        override.sourceId != null ? override.sourceId : sourceId,
        override.jobId != null ? override.jobId : jobId,
        override.mapperId != null ? override.mapperId : mapperId,
        override.importId != null ? override.importId : importId);
  }

  public boolean isEmpty() {
    return sourceId == null && jobId == null && mapperId == null && importId == null;
  }

  String toHeaderJson() {
    var json = new StringBuilder("{");
    appendContextValue(json, DeadLetterFailureIndexer.SOURCE_ID_CONTEXT_KEY, sourceId);
    appendContextValue(json, DeadLetterFailureIndexer.JOB_ID_CONTEXT_KEY, jobId);
    appendContextValue(json, DeadLetterFailureIndexer.MAPPER_ID_CONTEXT_KEY, mapperId);
    appendContextValue(json, DeadLetterFailureIndexer.IMPORT_ID_CONTEXT_KEY, importId);
    return json.append('}').toString();
  }

  private static void appendContextValue(StringBuilder json, String key, UUID value) {
    if (value == null) {
      return;
    }
    if (json.length() > 1) {
      json.append(',');
    }
    json.append('"').append(key).append("\":\"").append(value).append('"');
  }

  private static UUID uuidHeader(Headers headers, String headerName) {
    var header = headers.lastHeader(headerName);
    if (header == null || header.value() == null) {
      return null;
    }
    try {
      return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException _) {
      return null;
    }
  }
}
