// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC;

import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeadLetterFailureIndexer {

  public static final String REPLAY_COUNT_HEADER = "opencop-dlt-replay-count";
  public static final String CONTEXT_HEADER = "opencop-dlt-context";
  public static final String SOURCE_ID_CONTEXT_KEY = "sourceId";
  public static final String JOB_ID_CONTEXT_KEY = "jobId";
  public static final String MAPPER_ID_CONTEXT_KEY = "mapperId";
  public static final String IMPORT_ID_CONTEXT_KEY = "importId";

  private static final int MAX_JSON_BYTES = 16 * 1024;
  private static final int MAX_EXCEPTION_MESSAGE_CHARS = 4 * 1024;
  private static final String UNKNOWN = "UNKNOWN";
  private static final String EMPTY_JSON_OBJECT = "{}";
  private static final String FAILURE_ID_NAMESPACE = "processor-dlt-coordinate";
  private static final Duration DEFAULT_PAYLOAD_RETENTION = Duration.ofDays(7);
  private static final TypeReference<Map<String, Object>> CONTEXT_TYPE = new TypeReference<>() {};
  private static final Set<String> DURABLE_HEADER_ALLOWLIST =
      Set.of(
          DeadLetterHeaders.STAGE,
          DeadLetterHeaders.FAILURE_KIND,
          DeadLetterHeaders.DIAGNOSTIC_CATEGORY,
          DeadLetterHeaders.DIAGNOSTIC_FIELD,
          DLT_ORIGINAL_TOPIC,
          DLT_ORIGINAL_PARTITION,
          DLT_ORIGINAL_OFFSET,
          DLT_EXCEPTION_FQCN,
          REPLAY_COUNT_HEADER);
  private static final List<String> DURABLE_CONTEXT_ALLOWLIST =
      List.of(
          SOURCE_ID_CONTEXT_KEY, JOB_ID_CONTEXT_KEY, MAPPER_ID_CONTEXT_KEY, IMPORT_ID_CONTEXT_KEY);
  private static final Pattern SENSITIVE_DETAIL_PATTERN =
      Pattern.compile(
          "(?i)(authorization|cookie|password|passwd|secret|token|api[-_]?key|access[-_]?key)"
              + "\\s*[:=]\\s*[^\\s,;]+");

  private final ProcessorDeadLetterRecordRepository repository;
  private final ObjectMapper objectMapper;
  private final DeadLetterObservability observability;
  private final Duration payloadRetention;

  private record NewFailure(
      DeadLetterRecord deadLetterRecord, String topicOrSourceClass, long recordTimestampMs) {}

  public DeadLetterFailureIndexer(
      ProcessorDeadLetterRecordRepository repository,
      ObjectMapper objectMapper,
      DeadLetterObservability observability,
      KafkaConsumersProperties consumersProperties) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.observability = observability;
    var configuredPayloadRetention = consumersProperties.getDltIndexing().getPayloadRetention();
    this.payloadRetention =
        configuredPayloadRetention == null ? DEFAULT_PAYLOAD_RETENTION : configuredPayloadRetention;
  }

  public UUID index(ConsumerRecord<?, byte[]> dltMessage) {
    return indexBatch(List.of(dltMessage)).get(0);
  }

  public List<UUID> indexBatch(List<? extends ConsumerRecord<?, byte[]>> dltMessages) {
    var batchSource = dltMessages.isEmpty() ? DeadLetterObservability.UNKNOWN : null;
    var newFailures = new ArrayList<NewFailure>();
    var ids = new ArrayList<UUID>(dltMessages.size());
    var existingByCoordinate =
        repository.findByDltCoordinates(
            dltMessages.stream()
                .map(
                    dltMessage ->
                        new ProcessorDeadLetterRecordRepository.DltCoordinate(
                            dltMessage.topic(), dltMessage.partition(), dltMessage.offset()))
                .toList());
    for (ConsumerRecord<?, byte[]> dltMessage : dltMessages) {
      var contextValue = headerValue(dltMessage, CONTEXT_HEADER);
      var topicOrSourceClass = topicOrSourceClass(dltMessage.topic(), contextValue);
      if (batchSource == null) {
        batchSource = topicOrSourceClass;
      }
      observability.recordSeen(topicOrSourceClass);
      var context = parseContext(contextValue);
      recordContextParseFailure(topicOrSourceClass, contextValue, context);
      var coordinate =
          new ProcessorDeadLetterRecordRepository.DltCoordinate(
              dltMessage.topic(), dltMessage.partition(), dltMessage.offset());
      var existingRecord = existingByCoordinate.get(coordinate);
      if (existingRecord != null) {
        observability.recordSkipped(topicOrSourceClass);
        ids.add(existingRecord.id());
        continue;
      }
      var failure = toRecord(dltMessage, context);
      newFailures.add(new NewFailure(failure, topicOrSourceClass, dltMessage.timestamp()));
    }
    if (newFailures.isEmpty()) {
      return ids;
    }
    var indexed =
        repository.upsertAll(
            newFailures.stream().map(NewFailure::deadLetterRecord).toList(), batchSource);
    for (int index = 0; index < indexed.size(); index++) {
      var failure = indexed.get(index);
      var candidate = newFailures.get(index);
      observability.recordIndexed(candidate.topicOrSourceClass(), candidate.recordTimestampMs());
      ids.add(failure.id());
    }
    return ids;
  }

  private DeadLetterRecord toRecord(
      ConsumerRecord<?, byte[]> dltMessage, Optional<Map<String, Object>> context) {
    var failureId = failureId(dltMessage);
    var indexedAt = Instant.now();
    return new DeadLetterRecord(
        failureId,
        DeadLetterRecord.State.OPEN,
        indexedAt,
        indexedAt,
        stage(headerValue(dltMessage, DeadLetterHeaders.STAGE)),
        failureKind(headerValue(dltMessage, DeadLetterHeaders.FAILURE_KIND)),
        stringOrUnknown(headerValue(dltMessage, DLT_ORIGINAL_TOPIC)),
        integerHeader(dltMessage, DLT_ORIGINAL_PARTITION),
        longHeader(dltMessage, DLT_ORIGINAL_OFFSET),
        dltMessage.topic(),
        dltMessage.partition(),
        dltMessage.offset(),
        stringOrUnknown(headerValue(dltMessage, DLT_EXCEPTION_FQCN)),
        sanitizedExceptionMessage(headerValue(dltMessage, DLT_EXCEPTION_MESSAGE)),
        Math.max(0, integerHeader(dltMessage, REPLAY_COUNT_HEADER, 0)),
        boundedContext(context),
        boundedHeaders(dltMessage),
        kafkaPayloadRef(dltMessage),
        null,
        null,
        payloadExpiresAt(dltMessage, indexedAt),
        indexedAt,
        adminContext(context));
  }

  private static String kafkaPayloadRef(ConsumerRecord<?, ?> dltMessage) {
    return "kafka://%s/%d/%d"
        .formatted(dltMessage.topic(), dltMessage.partition(), dltMessage.offset());
  }

  private Instant payloadExpiresAt(ConsumerRecord<?, ?> dltMessage, Instant indexedAt) {
    var timestamp = dltMessage.timestamp();
    var payloadTimestamp = timestamp >= 0 ? Instant.ofEpochMilli(timestamp) : indexedAt;
    // Expected availability only, suitable for "payload expected until" labels. Actual retention
    // can drift for fixed DLT topics or already-created BYOD DLT topics, and broker retention is
    // intentionally not queried on this indexing hot path.
    return payloadTimestamp.plus(payloadRetention);
  }

  private void recordContextParseFailure(
      String topicOrSourceClass, String contextValue, Optional<Map<String, Object>> context) {
    if (contextValue == null || contextValue.isBlank()) {
      return;
    }
    if (context.isEmpty()) {
      observability.recordFailure(topicOrSourceClass, DeadLetterObservability.FailureCause.PARSE);
    }
  }

  private static String topicOrSourceClass(String topic, String contextValue) {
    return contextValue != null && contextValue.contains("\"" + SOURCE_ID_CONTEXT_KEY + "\"")
        ? DeadLetterObservability.BYOD
        : topic;
  }

  private String boundedHeaders(ConsumerRecord<?, byte[]> dltMessage) {
    var headers = new LinkedHashMap<String, String>();
    for (Header header : dltMessage.headers()) {
      if (!DURABLE_HEADER_ALLOWLIST.contains(header.key())) {
        continue;
      }
      String value = headerValue(header);
      headers.put(header.key(), value == null ? null : truncateUtf8(value, MAX_JSON_BYTES));
    }
    return boundedJson(headers);
  }

  private String boundedJson(LinkedHashMap<String, String> value) {
    var json = json(value);
    var jsonByteLength = utf8Length(json);
    while (jsonByteLength > MAX_JSON_BYTES && shrinkLargestValue(value)) {
      json = json(value);
      jsonByteLength = utf8Length(json);
    }
    return jsonByteLength <= MAX_JSON_BYTES ? json : EMPTY_JSON_OBJECT;
  }

  private static boolean shrinkLargestValue(LinkedHashMap<String, String> value) {
    String largestKey = null;
    var largestLength = 0;
    for (Map.Entry<String, String> entry : value.entrySet()) {
      if (entry.getValue() != null && entry.getValue().length() > largestLength) {
        largestKey = entry.getKey();
        largestLength = entry.getValue().length();
      }
    }
    if (largestKey == null || largestLength <= UNKNOWN.length()) {
      return false;
    }
    value.put(
        largestKey,
        truncateCharacters(value.get(largestKey), Math.max(UNKNOWN.length(), largestLength / 2)));
    return true;
  }

  private String boundedContext(Optional<Map<String, Object>> context) {
    if (context.isEmpty()) {
      return EMPTY_JSON_OBJECT;
    }
    var contextValue = context.orElse(Map.of());
    var sanitized = new LinkedHashMap<String, String>();
    for (String allowedKey : DURABLE_CONTEXT_ALLOWLIST) {
      Object allowedValue = contextValue.get(allowedKey);
      if (allowedValue != null) {
        sanitized.put(allowedKey, truncateUtf8(allowedValue.toString(), MAX_JSON_BYTES));
      }
    }
    return boundedJson(sanitized);
  }

  private DeadLetterRecord.AdminContext adminContext(Optional<Map<String, Object>> context) {
    if (context.isEmpty()) {
      return DeadLetterRecord.AdminContext.empty();
    }
    var contextValue = context.orElse(Map.of());
    return new DeadLetterRecord.AdminContext(
        uuidContextValue(contextValue, SOURCE_ID_CONTEXT_KEY),
        uuidContextValue(contextValue, JOB_ID_CONTEXT_KEY),
        uuidContextValue(contextValue, MAPPER_ID_CONTEXT_KEY),
        uuidContextValue(contextValue, IMPORT_ID_CONTEXT_KEY));
  }

  private Optional<Map<String, Object>> parseContext(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(value, CONTEXT_TYPE));
    } catch (JacksonException _) {
      return Optional.empty();
    }
  }

  private static UUID uuidContextValue(Map<String, Object> context, String key) {
    Object value = context.get(key);
    if (value == null) {
      return null;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private String json(Map<String, String> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException _) {
      return EMPTY_JSON_OBJECT;
    }
  }

  private static DeadLetterRecord.Stage stage(String value) {
    try {
      return value == null ? DeadLetterRecord.Stage.UNKNOWN : DeadLetterRecord.Stage.valueOf(value);
    } catch (IllegalArgumentException _) {
      return DeadLetterRecord.Stage.UNKNOWN;
    }
  }

  private static DeadLetterRecord.FailureKind failureKind(String value) {
    try {
      return value == null
          ? DeadLetterRecord.FailureKind.UNKNOWN
          : DeadLetterRecord.FailureKind.valueOf(value);
    } catch (IllegalArgumentException _) {
      return DeadLetterRecord.FailureKind.UNKNOWN;
    }
  }

  private static Integer integerHeader(ConsumerRecord<?, ?> dltMessage, String headerName) {
    String value = headerValue(dltMessage, headerName);
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException _) {
      return null;
    }
  }

  private static int integerHeader(
      ConsumerRecord<?, ?> dltMessage, String headerName, int fallback) {
    Integer value = integerHeader(dltMessage, headerName);
    return value == null ? fallback : value;
  }

  private static Long longHeader(ConsumerRecord<?, ?> dltMessage, String headerName) {
    String value = headerValue(dltMessage, headerName);
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException _) {
      return null;
    }
  }

  private static String headerValue(ConsumerRecord<?, ?> dltMessage, String headerName) {
    Header header = dltMessage.headers().lastHeader(headerName);
    return header == null ? null : headerValue(header);
  }

  private static String headerValue(Header header) {
    if (header.value() == null) {
      return null;
    }
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private static UUID failureId(ConsumerRecord<?, ?> dltMessage) {
    var topic = dltMessage.topic() == null ? "" : dltMessage.topic();
    var coordinate =
        String.join(
            ":",
            FAILURE_ID_NAMESPACE,
            String.valueOf(topic.length()),
            topic,
            String.valueOf(dltMessage.partition()),
            String.valueOf(dltMessage.offset()));
    return UUID.nameUUIDFromBytes(coordinate.getBytes(StandardCharsets.UTF_8));
  }

  private static String stringOrUnknown(String value) {
    return value == null || value.isBlank() ? UNKNOWN : value;
  }

  private static String sanitizedExceptionMessage(String value) {
    var message = stringOrUnknown(value);
    var sanitized = SENSITIVE_DETAIL_PATTERN.matcher(message).replaceAll("$1=[REDACTED]");
    return truncateCharacters(sanitized, MAX_EXCEPTION_MESSAGE_CHARS);
  }

  private static String truncateCharacters(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String truncateUtf8(String value, int maxBytes) {
    if (utf8Length(value) <= maxBytes) {
      return value;
    }
    var builder = new StringBuilder();
    var usedBytes = 0;
    for (int i = 0; i < value.length(); i++) {
      String next = Character.toString(value.charAt(i));
      int nextBytes = next.getBytes(StandardCharsets.UTF_8).length;
      if (usedBytes + nextBytes > maxBytes) {
        break;
      }
      builder.append(next);
      usedBytes += nextBytes;
    }
    return builder.toString();
  }

  private static int utf8Length(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }
}
