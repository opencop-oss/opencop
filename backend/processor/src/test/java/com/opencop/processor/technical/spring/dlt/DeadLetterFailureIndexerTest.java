// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_FQCN;
import static org.springframework.kafka.support.KafkaHeaders.DLT_EXCEPTION_MESSAGE;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_OFFSET;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_PARTITION;
import static org.springframework.kafka.support.KafkaHeaders.DLT_ORIGINAL_TOPIC;

import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import com.opencop.processor.technical.spring.config.broker.KafkaConsumersProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.ObjectMapper;

@SpringJUnitConfig(DeadLetterRepositoryJpaTestConfig.class)
class DeadLetterFailureIndexerTest {

  private static final String DLT_TOPIC = "opencop.geo.validated.dlt";
  private static final byte[] PAYLOAD = "failed-payload".getBytes(StandardCharsets.UTF_8);
  private static final Duration DEFAULT_PAYLOAD_RETENTION = Duration.ofDays(7);
  private static final int MAX_JSON_BYTES = 16 * 1024;
  private static final int MAX_EXCEPTION_MESSAGE_CHARS = 4 * 1024;
  private static final String EMPTY_JSON_OBJECT = "{}";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
  private static final UUID IMPORT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440104");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProcessorDeadLetterRecordRepository repository;
  @Autowired private ObjectMapper objectMapper;
  private BlobClient blobClient;
  private SimpleMeterRegistry meterRegistry;
  private DeadLetterObservability observability;
  private DeadLetterFailureIndexer indexer;

  @BeforeEach
  void setUp() {
    blobClient = mock(BlobClient.class);
    meterRegistry = new SimpleMeterRegistry();
    observability = new DeadLetterObservability(new ProcessorMetrics(meterRegistry));
    indexer =
        new DeadLetterFailureIndexer(
            repository,
            objectMapper,
            observability,
            consumersProperties(DEFAULT_PAYLOAD_RETENTION));
    jdbc.execute("TRUNCATE TABLE processor.processor_dead_letter_record CASCADE");
  }

  @Test
  void indexStoresKafkaPayloadReferenceExpiryAndMetadataWithoutUploadingPayload() {
    var timestamp = Instant.parse("2026-06-18T12:34:56Z");
    var dltMessage = dltMessage(DLT_TOPIC, 2, 91L, timestamp.toEpochMilli(), PAYLOAD);
    addHeader(dltMessage.headers(), DeadLetterHeaders.STAGE, "REAL_TIME");
    addHeader(dltMessage.headers(), DeadLetterHeaders.FAILURE_KIND, "PROCESSING");
    addHeader(dltMessage.headers(), DeadLetterHeaders.DIAGNOSTIC_CATEGORY, "INVALID_MAPPING");
    addHeader(dltMessage.headers(), DeadLetterHeaders.DIAGNOSTIC_FIELD, "point.lat");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_TOPIC, "opencop.geo.validated");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_PARTITION, "1");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_OFFSET, "42");
    addHeader(dltMessage.headers(), DLT_EXCEPTION_FQCN, "java.lang.IllegalArgumentException");
    addHeader(dltMessage.headers(), DLT_EXCEPTION_MESSAGE, "bad feature");
    addHeader(dltMessage.headers(), DeadLetterFailureIndexer.REPLAY_COUNT_HEADER, "5");
    addHeader(
        dltMessage.headers(), DeadLetterFailureIndexer.CONTEXT_HEADER, "{\"source\":\"byod\"}");

    var failureId = indexer.index(dltMessage);

    var persisted = repository.findById(failureId).orElseThrow();
    assertThat(repository.count()).isEqualTo(1);
    assertThat(persisted.payloadRef()).isEqualTo("kafka://opencop.geo.validated.dlt/2/91");
    assertThat(persisted.payloadExpiresAt()).isEqualTo(timestamp.plus(DEFAULT_PAYLOAD_RETENTION));
    assertThat(persisted.payloadSha256()).isNull();
    assertThat(persisted.payloadSizeBytes()).isNull();
    assertThat(persisted.stage()).isEqualTo(DeadLetterRecord.Stage.REAL_TIME);
    assertThat(persisted.failureKind()).isEqualTo(DeadLetterRecord.FailureKind.PROCESSING);
    assertThat(persisted.originalTopic()).isEqualTo("opencop.geo.validated");
    assertThat(persisted.originalPartition()).isEqualTo(1);
    assertThat(persisted.originalOffset()).isEqualTo(42L);
    assertThat(persisted.dltTopic()).isEqualTo(DLT_TOPIC);
    assertThat(persisted.dltPartition()).isEqualTo(2);
    assertThat(persisted.dltOffset()).isEqualTo(91L);
    assertThat(persisted.exceptionClass()).isEqualTo("java.lang.IllegalArgumentException");
    assertThat(persisted.exceptionMessage()).isEqualTo("bad feature");
    assertThat(persisted.replayCount()).isEqualTo(5);
    assertThat(persisted.contextJson()).isEqualTo(EMPTY_JSON_OBJECT);
    assertThat(persisted.headersJson()).contains("\"opencop-dlt-stage\":\"REAL_TIME\"");
    assertThat(persisted.headersJson())
        .contains("\"opencop-dlt-diagnostic-category\":\"INVALID_MAPPING\"")
        .contains("\"opencop-dlt-diagnostic-field\":\"point.lat\"");
    assertThat(persisted.headersJson()).doesNotContain("kafka_dlt-exception-message");
    assertThat(persisted.state()).isEqualTo(DeadLetterRecord.State.OPEN);
    assertThat(persisted.stateUpdatedAt()).isNotNull();
    assertThat(persisted.updatedAt()).isNotNull();
    verify(blobClient, never()).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void duplicateDltCoordinatesReturnExistingIdWithoutReplacingMetadata() {
    var firstMessage = dltMessage(DLT_TOPIC, 0, 7L, "first".getBytes(StandardCharsets.UTF_8));
    addWellFormedHeaders(firstMessage.headers());
    var secondMessage = dltMessage(DLT_TOPIC, 0, 7L, "second".getBytes(StandardCharsets.UTF_8));
    addWellFormedHeaders(secondMessage.headers());

    var firstId = indexer.index(firstMessage);
    var firstRecord = repository.findById(firstId).orElseThrow();
    var secondId = indexer.index(secondMessage);

    assertThat(secondId).isEqualTo(firstId);
    assertThat(repository.count()).isEqualTo(1);
    assertThat(repository.findById(firstId).orElseThrow()).isEqualTo(firstRecord);
    assertThat(recordCount(DLT_TOPIC, "seen")).isEqualTo(2.0);
    assertThat(recordCount(DLT_TOPIC, "indexed")).isEqualTo(1.0);
    assertThat(recordCount(DLT_TOPIC, "skipped")).isEqualTo(1.0);
    verify(blobClient, never()).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void indexUsesConfiguredPayloadRetentionFromKafkaTimestamp() {
    var payloadRetention = Duration.ofHours(6);
    var retainedIndexer =
        new DeadLetterFailureIndexer(
            repository, objectMapper, observability, consumersProperties(payloadRetention));
    var timestamp = Instant.parse("2026-06-18T12:34:56Z");
    var dltMessage = dltMessage(DLT_TOPIC, 0, 81L, timestamp.toEpochMilli(), PAYLOAD);
    addWellFormedHeaders(dltMessage.headers());

    var failureId = retainedIndexer.index(dltMessage);

    assertThat(repository.findById(failureId).orElseThrow().payloadExpiresAt())
        .isEqualTo(timestamp.plus(payloadRetention));
  }

  @Test
  void indexFallsBackToIndexingTimeWhenKafkaTimestampIsUnusable() {
    var payloadRetention = Duration.ofHours(9);
    var retainedIndexer =
        new DeadLetterFailureIndexer(
            repository, objectMapper, observability, consumersProperties(payloadRetention));
    var dltMessage = dltMessage(DLT_TOPIC, 0, 82L, PAYLOAD);
    addWellFormedHeaders(dltMessage.headers());

    var before = Instant.now().plus(payloadRetention);
    var failureId = retainedIndexer.index(dltMessage);
    var after = Instant.now().plus(payloadRetention);

    assertThat(repository.findById(failureId).orElseThrow().payloadExpiresAt())
        .isBetween(before, after);
  }

  @Test
  void indexBatchPreservesFirstMetadataForDuplicateDltCoordinates() {
    var firstMessage = dltMessage(DLT_TOPIC, 0, 8L, "first".getBytes(StandardCharsets.UTF_8));
    addWellFormedHeaders(firstMessage.headers());
    addHeader(firstMessage.headers(), DLT_EXCEPTION_MESSAGE, "first failure");
    var duplicateMessage = dltMessage(DLT_TOPIC, 0, 8L, "second".getBytes(StandardCharsets.UTF_8));
    addWellFormedHeaders(duplicateMessage.headers());
    addHeader(duplicateMessage.headers(), DLT_EXCEPTION_MESSAGE, "second failure");

    var ids = indexer.indexBatch(List.of(firstMessage, duplicateMessage));

    assertThat(ids).hasSize(2).containsOnly(ids.getFirst());
    assertThat(repository.count()).isEqualTo(1);
    var persisted = repository.findByDltCoordinates(DLT_TOPIC, 0, 8L).orElseThrow();
    assertThat(persisted.exceptionMessage()).isEqualTo("first failure");
  }

  @Test
  void retryAfterRepositoryFailureDoesNotUploadPayloadBytes() {
    var failingRepository = mock(ProcessorDeadLetterRecordRepository.class);
    when(failingRepository.findByDltCoordinates(any(List.class))).thenReturn(Map.of());
    when(failingRepository.upsertAll(any(), any()))
        .thenThrow(new IllegalStateException("db unavailable"));
    var retryingIndexer =
        new DeadLetterFailureIndexer(
            failingRepository,
            objectMapper,
            new DeadLetterObservability(ProcessorMetrics.noop()),
            consumersProperties(DEFAULT_PAYLOAD_RETENTION));
    var dltMessage = dltMessage(DLT_TOPIC, 0, 7L, PAYLOAD);
    addWellFormedHeaders(dltMessage.headers());

    assertThatThrownBy(() -> retryingIndexer.index(dltMessage))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> retryingIndexer.index(dltMessage))
        .isInstanceOf(IllegalStateException.class);

    verify(blobClient, never()).upload(any(String.class), any(InputStream.class));
  }

  @Test
  void indexBatchPersistsNewRecordsWithOneRepositoryBatchCall() {
    var batchRepository = mock(ProcessorDeadLetterRecordRepository.class);
    when(batchRepository.findByDltCoordinates(any(List.class))).thenReturn(Map.of());
    var batchIndexer =
        new DeadLetterFailureIndexer(
            batchRepository,
            objectMapper,
            new DeadLetterObservability(ProcessorMetrics.noop()),
            consumersProperties(DEFAULT_PAYLOAD_RETENTION));
    var first = dltMessage(DLT_TOPIC, 0, 10L, PAYLOAD);
    var second = dltMessage(DLT_TOPIC, 0, 11L, PAYLOAD);
    addWellFormedHeaders(first.headers());
    addWellFormedHeaders(second.headers());

    batchIndexer.indexBatch(List.of(first, second));

    @SuppressWarnings("unchecked")
    var recordsCaptor = ArgumentCaptor.forClass(List.class);
    verify(batchRepository).upsertAll(recordsCaptor.capture(), eq(DLT_TOPIC));
    verify(batchRepository, never())
        .findByDltCoordinates(eq(DLT_TOPIC), any(Integer.class), any(Long.class));
    assertThat(recordsCaptor.getValue())
        .extracting(record -> ((DeadLetterRecord) record).dltOffset())
        .containsExactly(10L, 11L);
  }

  @Test
  void indexRecordsSeenIndexedLagLastIndexedAndParseMetrics() {
    var sourceId = UUID.randomUUID();
    var dltMessage =
        dltMessage(
            "opencop.byod.test.%s.dlt".formatted(sourceId),
            0,
            101L,
            System.currentTimeMillis() - 1_000,
            PAYLOAD);
    addWellFormedHeaders(dltMessage.headers());
    addHeader(
        dltMessage.headers(),
        DeadLetterFailureIndexer.CONTEXT_HEADER,
        "{\"sourceId\":\"%s\"}".formatted(sourceId));

    indexer.index(dltMessage);

    assertThat(recordCount(DeadLetterObservability.BYOD, "seen")).isEqualTo(1.0);
    assertThat(recordCount(DeadLetterObservability.BYOD, "indexed")).isEqualTo(1.0);
    assertThat(
            meterRegistry
                .find(DeadLetterObservability.INDEXING_LAG_METRIC)
                .tag(
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS, DeadLetterObservability.BYOD)
                .summary()
                .count())
        .isEqualTo(1);
    assertThat(
            meterRegistry
                .find(DeadLetterObservability.LAST_INDEXED_TIME_METRIC)
                .tag(
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS, DeadLetterObservability.BYOD)
                .gauge()
                .value())
        .isPositive();
    var malformed = dltMessage(DLT_TOPIC, 0, 102L, PAYLOAD);
    addWellFormedHeaders(malformed.headers());
    addHeader(malformed.headers(), DeadLetterFailureIndexer.CONTEXT_HEADER, "{not-json");

    indexer.index(malformed);

    assertThat(
            meterRegistry
                .counter(
                    DeadLetterObservability.FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    DLT_TOPIC,
                    DeadLetterObservability.TAG_CAUSE,
                    "parse")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void nullAndMalformedHeadersAreIndexedAsSafeMetadata() {
    var dltMessage = dltMessage(DLT_TOPIC, 1, 19L, PAYLOAD);
    dltMessage.headers().add(new RecordHeader("nullable-header", null));
    dltMessage.headers().add(new RecordHeader(DLT_ORIGINAL_TOPIC, null));
    addHeader(dltMessage.headers(), DLT_ORIGINAL_PARTITION, "not-a-number");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_OFFSET, "also-not-a-number");
    addHeader(dltMessage.headers(), DeadLetterFailureIndexer.REPLAY_COUNT_HEADER, "-3");

    var failureId = indexer.index(dltMessage);

    var persisted = repository.findById(failureId).orElseThrow();
    assertThat(persisted.originalPartition()).isNull();
    assertThat(persisted.originalOffset()).isNull();
    assertThat(persisted.replayCount()).isZero();
    assertThat(persisted.headersJson()).contains("\"kafka_dlt-original-topic\":null");
    assertThat(persisted.headersJson()).doesNotContain("nullable-header");
  }

  @Test
  void sensitiveAndUnapprovedMetadataIsNotPersistedInDurableRegistry() {
    var dltMessage = dltMessage(DLT_TOPIC, 1, 21L, PAYLOAD);
    addWellFormedHeaders(dltMessage.headers());
    addHeader(dltMessage.headers(), DLT_EXCEPTION_MESSAGE, "password=hunter2 token=abc failed");
    addHeader(dltMessage.headers(), "Authorization", "Bearer should-not-persist");
    addHeader(dltMessage.headers(), "x-payload-fragment", "{\"secret\":\"payload\"}");
    addHeader(
        dltMessage.headers(),
        DeadLetterFailureIndexer.CONTEXT_HEADER,
        """
        {"%s":"%s","%s":"%s","%s":"%s","%s":"%s","authorization":"Bearer should-not-persist"}
        """
            .formatted(
                DeadLetterFailureIndexer.SOURCE_ID_CONTEXT_KEY,
                SOURCE_ID,
                DeadLetterFailureIndexer.JOB_ID_CONTEXT_KEY,
                JOB_ID,
                DeadLetterFailureIndexer.MAPPER_ID_CONTEXT_KEY,
                MAPPER_ID,
                DeadLetterFailureIndexer.IMPORT_ID_CONTEXT_KEY,
                IMPORT_ID));

    var failureId = indexer.index(dltMessage);

    var persisted = repository.findById(failureId).orElseThrow();
    assertThat(persisted.exceptionMessage())
        .isEqualTo("password=[REDACTED] token=[REDACTED] failed");
    assertThat(persisted.contextJson())
        .isEqualTo(
            """
            {"%s":"%s","%s":"%s","%s":"%s","%s":"%s"}
            """
                .formatted(
                    DeadLetterFailureIndexer.SOURCE_ID_CONTEXT_KEY,
                    SOURCE_ID,
                    DeadLetterFailureIndexer.JOB_ID_CONTEXT_KEY,
                    JOB_ID,
                    DeadLetterFailureIndexer.MAPPER_ID_CONTEXT_KEY,
                    MAPPER_ID,
                    DeadLetterFailureIndexer.IMPORT_ID_CONTEXT_KEY,
                    IMPORT_ID)
                .trim());
    assertThat(persisted.adminContext())
        .isEqualTo(new DeadLetterRecord.AdminContext(SOURCE_ID, JOB_ID, MAPPER_ID, IMPORT_ID));
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
                SOURCE_ID))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_JOB_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.JOB_ID_COLUMN,
                JOB_ID))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_MAPPER_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.MAPPER_ID_COLUMN,
                MAPPER_ID))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_IMPORT_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.IMPORT_ID_COLUMN,
                IMPORT_ID))
        .isEqualTo(1);
    assertThat(persisted.headersJson())
        .contains("\"opencop-dlt-stage\":\"REAL_TIME\"")
        .doesNotContain("Authorization")
        .doesNotContain("x-payload-fragment")
        .doesNotContain("should-not-persist")
        .doesNotContain("hunter2")
        .doesNotContain("abc");
  }

  @Test
  void malformedAndOversizedHeadersAreBoundedAndOutOfContractFieldsBecomeUnknown() {
    var dltMessage = dltMessage(DLT_TOPIC, 3, 11L, PAYLOAD);
    addHeader(dltMessage.headers(), DeadLetterHeaders.STAGE, "NOT_A_STAGE");
    addHeader(dltMessage.headers(), DeadLetterHeaders.FAILURE_KIND, "NOT_A_KIND");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_TOPIC, "opencop.geo.validated");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_PARTITION, "not-an-int");
    addHeader(dltMessage.headers(), DLT_ORIGINAL_OFFSET, "not-a-long");
    addHeader(dltMessage.headers(), DLT_EXCEPTION_FQCN, "example.CustomException");
    addHeader(
        dltMessage.headers(), DLT_EXCEPTION_MESSAGE, "x".repeat(MAX_EXCEPTION_MESSAGE_CHARS + 128));
    addHeader(
        dltMessage.headers(),
        DeadLetterFailureIndexer.CONTEXT_HEADER,
        "c".repeat(MAX_JSON_BYTES + 128));
    addHeader(dltMessage.headers(), "oversized-header", "h".repeat(MAX_JSON_BYTES + 128));

    var failureId = indexer.index(dltMessage);

    var persisted = repository.findById(failureId).orElseThrow();
    assertThat(persisted.stage()).isEqualTo(DeadLetterRecord.Stage.UNKNOWN);
    assertThat(persisted.failureKind()).isEqualTo(DeadLetterRecord.FailureKind.UNKNOWN);
    assertThat(persisted.originalPartition()).isNull();
    assertThat(persisted.originalOffset()).isNull();
    assertThat(persisted.exceptionMessage()).hasSize(MAX_EXCEPTION_MESSAGE_CHARS);
    assertThat(persisted.headersJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
    assertThat(persisted.contextJson().getBytes(StandardCharsets.UTF_8))
        .hasSizeLessThanOrEqualTo(MAX_JSON_BYTES);
  }

  private static ConsumerRecord<String, byte[]> dltMessage(
      String topic, int partition, long offset, byte[] value) {
    return new ConsumerRecord<>(topic, partition, offset, "key", value);
  }

  private static ConsumerRecord<String, byte[]> dltMessage(
      String topic, int partition, long offset, long timestamp, byte[] value) {
    return new ConsumerRecord<>(
        topic,
        partition,
        offset,
        timestamp,
        TimestampType.CREATE_TIME,
        -1,
        value.length,
        "key",
        value,
        new RecordHeaders(),
        Optional.empty());
  }

  private static void addWellFormedHeaders(Headers headers) {
    addHeader(headers, DeadLetterHeaders.STAGE, "REAL_TIME");
    addHeader(headers, DeadLetterHeaders.FAILURE_KIND, "PROCESSING");
    addHeader(headers, DLT_ORIGINAL_TOPIC, "opencop.geo.validated");
    addHeader(headers, DLT_ORIGINAL_PARTITION, "1");
    addHeader(headers, DLT_ORIGINAL_OFFSET, "42");
    addHeader(headers, DLT_EXCEPTION_FQCN, "java.lang.IllegalArgumentException");
    addHeader(headers, DLT_EXCEPTION_MESSAGE, "bad feature");
  }

  private static void addHeader(Headers headers, String name, String value) {
    headers.add(name, value.getBytes(StandardCharsets.UTF_8));
  }

  private static KafkaConsumersProperties consumersProperties(Duration payloadRetention) {
    var properties = new KafkaConsumersProperties();
    properties.getDltIndexing().setPayloadRetention(payloadRetention);
    return properties;
  }

  private int contextCount(String table, String column, UUID value) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
  }

  private double recordCount(String topicOrSourceClass, String outcome) {
    return meterRegistry
        .counter(
            DeadLetterObservability.RECORDS_METRIC,
            DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
            topicOrSourceClass,
            DeadLetterObservability.TAG_OUTCOME,
            outcome)
        .count();
  }
}
