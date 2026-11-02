// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(DeadLetterRepositoryJpaTestConfig.class)
class ProcessorDeadLetterRecordRepositoryTest {

  private static final String DLT_TOPIC = "opencop.geo.validated.dlt";
  private static final int DLT_PARTITION = 2;
  private static final long DLT_OFFSET = 99L;
  private static final Instant INDEXED_AT = Instant.parse("2026-06-18T12:00:00Z");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private NamedParameterJdbcTemplate namedJdbc;
  @Autowired private ProcessorDeadLetterRecordRepository repository;

  @BeforeEach
  void setUp() {
    jdbc.execute("TRUNCATE TABLE processor.processor_dead_letter_record CASCADE");
  }

  @Test
  void upsertByDltCoordinatesReturnsStableIdAndPreservesOriginalMetadata() {
    var failureId = UUID.randomUUID();
    var firstFailure =
        newFailure(
            failureId,
            "kafka://opencop.geo.validated.dlt/2/99",
            Instant.parse("2026-06-25T12:00:00Z"));
    var inserted = repository.upsert(firstFailure);

    var duplicate =
        newFailure(
            UUID.randomUUID(),
            "kafka://opencop.geo.validated.dlt/2/99-replacement",
            Instant.parse("2026-06-26T12:00:00Z"),
            "opencop.geo.mapped",
            4,
            1234L);
    var upsertedDuplicate = repository.upsert(duplicate);

    assertThat(upsertedDuplicate.id()).isEqualTo(inserted.id());
    assertThat(repository.count()).isEqualTo(1);
    var persisted = repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, DLT_OFFSET);
    assertThat(persisted).isPresent();
    assertThat(persisted.orElseThrow().payloadSha256()).isNull();
    assertThat(persisted.orElseThrow().payloadSizeBytes()).isNull();
    assertThat(persisted.orElseThrow().payloadRef())
        .isEqualTo("kafka://opencop.geo.validated.dlt/2/99");
    assertThat(persisted.orElseThrow().payloadExpiresAt())
        .isEqualTo(Instant.parse("2026-06-25T12:00:00Z"));
    assertThat(persisted.orElseThrow().originalTopic()).isEqualTo("opencop.geo.validated");
    assertThat(persisted.orElseThrow().state()).isEqualTo(DeadLetterRecord.State.OPEN);
  }

  @Test
  void upsertProjectsAdminContextIntoTypedFilterTables() {
    var sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440101");
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440102");
    var mapperId = UUID.fromString("550e8400-e29b-41d4-a716-446655440103");
    var importId = UUID.fromString("550e8400-e29b-41d4-a716-446655440104");
    var failure =
        newFailure(UUID.randomUUID(), "kafka://opencop.geo.validated.dlt/2/99", INDEXED_AT)
            .withAdminContext(
                new DeadLetterRecord.AdminContext(sourceId, jobId, mapperId, importId));

    var inserted = repository.upsert(failure);

    assertThat(inserted.adminContext().sourceId()).isEqualTo(sourceId);
    assertThat(inserted.adminContext().jobId()).isEqualTo(jobId);
    assertThat(inserted.adminContext().mapperId()).isEqualTo(mapperId);
    assertThat(inserted.adminContext().importId()).isEqualTo(importId);
    assertThat(repository.findById(inserted.id()).orElseThrow().adminContext())
        .isEqualTo(new DeadLetterRecord.AdminContext(sourceId, jobId, mapperId, importId));
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
                sourceId))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_JOB_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.JOB_ID_COLUMN,
                jobId))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_MAPPER_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.MAPPER_ID_COLUMN,
                mapperId))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_IMPORT_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.IMPORT_ID_COLUMN,
                importId))
        .isEqualTo(1);
  }

  @Test
  void upsertAllPersistsMultipleDeadLetterRecordsInOneRepositoryCall() {
    var first =
        newFailure(
            UUID.randomUUID(),
            "kafka://opencop.geo.validated.dlt/2/99",
            Instant.parse("2026-06-25T12:00:00Z"));
    var second =
        newFailure(
            UUID.randomUUID(),
            "kafka://opencop.geo.validated.dlt/2/100",
            Instant.parse("2026-06-25T12:01:00Z"),
            "opencop.geo.validated",
            1,
            43L,
            100L);

    var inserted = repository.upsertAll(List.of(first, second), DLT_TOPIC);

    assertThat(inserted).extracting(DeadLetterRecord::id).containsExactly(first.id(), second.id());
    assertThat(repository.count()).isEqualTo(2);
    assertThat(repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, DLT_OFFSET)).isPresent();
    assertThat(repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, 100L)).isPresent();
  }

  @Test
  void upsertAllChunksLargeDistinctBatchesBelowPostgresParameterLimit() {
    var failures = new ArrayList<DeadLetterRecord>();
    for (int index = 0; index < 3_000; index++) {
      failures.add(
          newFailure(
              UUID.randomUUID(),
              "kafka://opencop.geo.validated.dlt/2/" + index,
              INDEXED_AT,
              "opencop.geo.validated",
              1,
              42L + index,
              index));
    }

    var inserted = repository.upsertAll(failures, DLT_TOPIC);

    assertThat(inserted).hasSize(3_000);
    assertThat(inserted).extracting(DeadLetterRecord::id).containsExactlyElementsOf(ids(failures));
    assertThat(repository.count()).isEqualTo(3_000);
    assertThat(repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, 0L)).isPresent();
    assertThat(repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, 2_999L)).isPresent();
  }

  @Test
  void upsertAllPreservesFirstRowAndAdminContextForDuplicateDltCoordinates() {
    var firstSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
    var secondSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");
    var first =
        newFailure(
                UUID.randomUUID(),
                "kafka://opencop.geo.validated.dlt/2/99",
                Instant.parse("2026-06-25T12:00:00Z"))
            .withAdminContext(new DeadLetterRecord.AdminContext(firstSourceId, null, null, null));
    var duplicate =
        newFailure(
                UUID.randomUUID(),
                "kafka://opencop.geo.validated.dlt/2/99-replacement",
                Instant.parse("2026-06-26T12:00:00Z"),
                "opencop.geo.mapped",
                4,
                1234L)
            .withAdminContext(new DeadLetterRecord.AdminContext(secondSourceId, null, null, null));

    var upserted = repository.upsertAll(List.of(first, duplicate), DLT_TOPIC);

    assertThat(upserted).extracting(DeadLetterRecord::id).containsExactly(first.id(), first.id());
    assertThat(repository.count()).isEqualTo(1);
    var persisted =
        repository.findByDltCoordinates(DLT_TOPIC, DLT_PARTITION, DLT_OFFSET).orElseThrow();
    assertThat(persisted.payloadRef()).isEqualTo("kafka://opencop.geo.validated.dlt/2/99");
    assertThat(persisted.payloadExpiresAt()).isEqualTo(Instant.parse("2026-06-25T12:00:00Z"));
    assertThat(persisted.originalTopic()).isEqualTo("opencop.geo.validated");
    assertThat(persisted.adminContext().sourceId()).isEqualTo(firstSourceId);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
                firstSourceId))
        .isEqualTo(1);
    assertThat(
            contextCount(
                DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
                DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
                secondSourceId))
        .isZero();
  }

  @Test
  void migrationCreatesAdminFilterIndexesForListQueries() {
    assertThat(columnIsNullable("payload_sha256")).isTrue();
    assertThat(columnIsNullable("payload_size_bytes")).isTrue();
    assertThat(columnIsNullable("payload_expires_at")).isFalse();
    assertThat(indexDefinition("idx_processor_dead_letter_record_dlt_topic_indexed_at"))
        .contains("dlt_topic", "indexed_at DESC");
    assertThat(indexDefinition("idx_processor_dead_letter_record_failure_kind_indexed_at"))
        .contains("failure_kind", "indexed_at DESC");
    assertThat(indexDefinition("idx_processor_dead_letter_record_stage_indexed_at"))
        .contains("stage", "indexed_at DESC");
  }

  @Test
  void upsertRecordsDbFailureMetricsWhenNativeInsertFails() {
    var entityManager = mock(EntityManager.class);
    var query = mock(Query.class);
    when(entityManager.createNativeQuery(
            any(String.class), eq(ProcessorDeadLetterRecordEntity.class)))
        .thenReturn(query);
    when(query.setParameter(any(String.class), any())).thenReturn(query);
    when(query.getSingleResult()).thenThrow(new IllegalStateException("db unavailable"));
    var registry = new SimpleMeterRegistry();
    var repository =
        new ProcessorDeadLetterRecordRepository(
            entityManager, namedJdbc, new DeadLetterObservability(new ProcessorMetrics(registry)));

    assertThatThrownBy(
            () ->
                repository.upsert(
                    newFailure(
                        UUID.randomUUID(), "kafka://opencop.geo.validated.dlt/2/99", INDEXED_AT),
                    DLT_TOPIC))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            registry
                .counter(
                    DeadLetterObservability.DB_UPSERT_FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    DLT_TOPIC)
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .counter(
                    DeadLetterObservability.FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    DLT_TOPIC,
                    DeadLetterObservability.TAG_CAUSE,
                    "db")
                .count())
        .isEqualTo(1.0);
  }

  private static DeadLetterRecord newFailure(
      UUID failureId, String payloadRef, Instant payloadExpiresAt) {
    return newFailure(
        failureId, payloadRef, payloadExpiresAt, "opencop.geo.validated", 1, 42L, DLT_OFFSET);
  }

  private static DeadLetterRecord newFailure(
      UUID failureId,
      String payloadRef,
      Instant payloadExpiresAt,
      String originalTopic,
      int originalPartition,
      long originalOffset) {
    return newFailure(
        failureId,
        payloadRef,
        payloadExpiresAt,
        originalTopic,
        originalPartition,
        originalOffset,
        DLT_OFFSET);
  }

  private static DeadLetterRecord newFailure(
      UUID failureId,
      String payloadRef,
      Instant payloadExpiresAt,
      String originalTopic,
      int originalPartition,
      long originalOffset,
      long dltOffset) {
    return new DeadLetterRecord(
        failureId,
        DeadLetterRecord.State.OPEN,
        INDEXED_AT,
        INDEXED_AT,
        DeadLetterRecord.Stage.REAL_TIME,
        DeadLetterRecord.FailureKind.PROCESSING,
        originalTopic,
        originalPartition,
        originalOffset,
        DLT_TOPIC,
        DLT_PARTITION,
        dltOffset,
        "java.lang.IllegalStateException",
        "failed",
        3,
        "{\"source\":\"test\"}",
        "{\"header\":\"value\"}",
        payloadRef,
        null,
        null,
        payloadExpiresAt,
        INDEXED_AT,
        DeadLetterRecord.AdminContext.empty());
  }

  private static List<UUID> ids(List<DeadLetterRecord> failures) {
    return failures.stream().map(DeadLetterRecord::id).toList();
  }

  private int contextCount(String table, String column, UUID value) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
  }

  private String indexDefinition(String indexName) {
    return jdbc.queryForObject(
        """
        SELECT indexdef
          FROM pg_indexes
         WHERE schemaname = 'processor'
           AND tablename = 'processor_dead_letter_record'
           AND indexname = ?
        """,
        String.class,
        indexName);
  }

  private boolean columnIsNullable(String columnName) {
    return jdbc.queryForObject(
        """
        SELECT is_nullable = 'YES'
          FROM information_schema.columns
         WHERE table_schema = 'processor'
           AND table_name = 'processor_dead_letter_record'
           AND column_name = ?
        """,
        Boolean.class,
        columnName);
  }
}
