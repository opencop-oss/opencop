// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ProcessorDeadLetterRecordRepository {

  private static final int MAX_RECORDS_PER_BATCH_INSERT = 1_000;

  private static final String UPSERT_ONE_SQL =
      """
      INSERT INTO processor.processor_dead_letter_record (
        id, state, state_updated_at, updated_at,
        stage, failure_kind, original_topic, original_partition, original_offset,
        dlt_topic, dlt_partition, dlt_offset, exception_class, exception_message,
        replay_count, context_json, headers_json, payload_ref, payload_sha256,
        payload_size_bytes, payload_expires_at, indexed_at
      )
      VALUES (
        :id, :state, :stateUpdatedAt, :updatedAt,
        :stage, :failureKind, :originalTopic, :originalPartition, :originalOffset,
        :dltTopic, :dltPartition, :dltOffset, :exceptionClass, :exceptionMessage,
        :replayCount, :contextJson, :headersJson, :payloadRef, :payloadSha256,
        :payloadSizeBytes, :payloadExpiresAt, :indexedAt
      )
      ON CONFLICT (dlt_topic, dlt_partition, dlt_offset) DO UPDATE SET
        id = processor.processor_dead_letter_record.id
      RETURNING *
      """;
  private static final String RECORD_COLUMNS =
      """
        id, state, state_updated_at, updated_at,
        stage, failure_kind, original_topic, original_partition, original_offset,
        dlt_topic, dlt_partition, dlt_offset, exception_class, exception_message,
        replay_count, context_json, headers_json, payload_ref, payload_sha256,
        payload_size_bytes, payload_expires_at, indexed_at
      """;
  private static final String RECORD_INSERT_COLUMNS = "(" + RECORD_COLUMNS + ")";
  private static final String RECORD_INSERT_VALUES =
      """
      (
        %s, %s, %s, %s,
        %s, %s, %s, %s, %s,
        %s, %s, %s, %s, %s,
        %s, %s, %s, %s, %s,
        %s, %s, %s
      )
      """;
  private static final String RECORD_CONFLICT_CLAUSE =
      """
      ON CONFLICT (dlt_topic, dlt_partition, dlt_offset) DO UPDATE SET
        id = processor.processor_dead_letter_record.id
      RETURNING *
      """;
  private static final String PARAM_ID = "id";
  private static final String PARAM_STATE = "state";
  private static final String PARAM_STATE_UPDATED_AT = "stateUpdatedAt";
  private static final String PARAM_UPDATED_AT = "updatedAt";
  private static final String PARAM_STAGE = "stage";
  private static final String PARAM_FAILURE_KIND = "failureKind";
  private static final String PARAM_ORIGINAL_TOPIC = "originalTopic";
  private static final String PARAM_ORIGINAL_PARTITION = "originalPartition";
  private static final String PARAM_ORIGINAL_OFFSET = "originalOffset";
  private static final String PARAM_DLT_TOPIC = "dltTopic";
  private static final String PARAM_DLT_PARTITION = "dltPartition";
  private static final String PARAM_DLT_OFFSET = "dltOffset";
  private static final String PARAM_EXCEPTION_CLASS = "exceptionClass";
  private static final String PARAM_EXCEPTION_MESSAGE = "exceptionMessage";
  private static final String PARAM_REPLAY_COUNT = "replayCount";
  private static final String PARAM_CONTEXT_JSON = "contextJson";
  private static final String PARAM_HEADERS_JSON = "headersJson";
  private static final String PARAM_PAYLOAD_REF = "payloadRef";
  private static final String PARAM_PAYLOAD_SHA256 = "payloadSha256";
  private static final String PARAM_PAYLOAD_SIZE_BYTES = "payloadSizeBytes";
  private static final String PARAM_PAYLOAD_EXPIRES_AT = "payloadExpiresAt";
  private static final String PARAM_INDEXED_AT = "indexedAt";

  private final EntityManager entityManager;
  private final NamedParameterJdbcTemplate jdbc;
  private final DeadLetterObservability observability;

  public ProcessorDeadLetterRecordRepository(
      EntityManager entityManager,
      NamedParameterJdbcTemplate jdbc,
      DeadLetterObservability observability) {
    this.entityManager = entityManager;
    this.jdbc = jdbc;
    this.observability = observability;
  }

  public DeadLetterRecord upsert(DeadLetterRecord failure) {
    return upsert(failure, failure.dltTopic());
  }

  public DeadLetterRecord upsert(DeadLetterRecord failure, String topicOrSourceClass) {
    try {
      return upsertOne(failure);
    } catch (RuntimeException ex) {
      observability.recordDbUpsertFailure(topicOrSourceClass);
      throw ex;
    }
  }

  public List<DeadLetterRecord> upsertAll(
      List<DeadLetterRecord> failures, String topicOrSourceClass) {
    try {
      if (failures.isEmpty()) {
        return List.of();
      }
      return upsertMany(failures);
    } catch (RuntimeException ex) {
      observability.recordDbUpsertFailure(topicOrSourceClass);
      throw ex;
    }
  }

  private DeadLetterRecord upsertOne(DeadLetterRecord failure) {
    var entity = ProcessorDeadLetterRecordEntity.from(failure);
    var persisted =
        (ProcessorDeadLetterRecordEntity)
            entityManager
                .createNativeQuery(UPSERT_ONE_SQL, ProcessorDeadLetterRecordEntity.class)
                .setParameter(PARAM_ID, entity.getId())
                .setParameter(PARAM_STATE, entity.getState().name())
                .setParameter(PARAM_STATE_UPDATED_AT, entity.getStateUpdatedAt())
                .setParameter(PARAM_UPDATED_AT, entity.getUpdatedAt())
                .setParameter(PARAM_STAGE, entity.getStage().name())
                .setParameter(PARAM_FAILURE_KIND, entity.getFailureKind().name())
                .setParameter(PARAM_ORIGINAL_TOPIC, entity.getOriginalTopic())
                .setParameter(PARAM_ORIGINAL_PARTITION, entity.getOriginalPartition())
                .setParameter(PARAM_ORIGINAL_OFFSET, entity.getOriginalOffset())
                .setParameter(PARAM_DLT_TOPIC, entity.getDltTopic())
                .setParameter(PARAM_DLT_PARTITION, entity.getDltPartition())
                .setParameter(PARAM_DLT_OFFSET, entity.getDltOffset())
                .setParameter(PARAM_EXCEPTION_CLASS, entity.getExceptionClass())
                .setParameter(PARAM_EXCEPTION_MESSAGE, entity.getExceptionMessage())
                .setParameter(PARAM_REPLAY_COUNT, entity.getReplayCount())
                .setParameter(PARAM_CONTEXT_JSON, entity.getContextJson())
                .setParameter(PARAM_HEADERS_JSON, entity.getHeadersJson())
                .setParameter(PARAM_PAYLOAD_REF, entity.getPayloadRef())
                .setParameter(PARAM_PAYLOAD_SHA256, entity.getPayloadSha256())
                .setParameter(PARAM_PAYLOAD_SIZE_BYTES, entity.getPayloadSizeBytes())
                .setParameter(PARAM_PAYLOAD_EXPIRES_AT, entity.getPayloadExpiresAt())
                .setParameter(PARAM_INDEXED_AT, entity.getIndexedAt())
                .getSingleResult();
    if (persisted.getId().equals(failure.id())) {
      upsertAdminContext(persisted.getId(), failure.adminContext());
    }
    return attachAdminContext(persisted.toRecord());
  }

  private List<DeadLetterRecord> upsertMany(List<DeadLetterRecord> failures) {
    var firstByCoordinate = new LinkedHashMap<DltCoordinate, DeadLetterRecord>();
    for (DeadLetterRecord failure : failures) {
      firstByCoordinate.putIfAbsent(DltCoordinate.from(failure), failure);
    }

    var uniqueFailures = List.copyOf(firstByCoordinate.values());
    var persistedByCoordinate = batchUpsertRecordsInChunks(uniqueFailures);
    batchUpsertAdminContexts(uniqueFailures, persistedByCoordinate);
    var persistedWithContext = attachAdminContexts(new ArrayList<>(persistedByCoordinate.values()));

    var recordsByCoordinate = new HashMap<DltCoordinate, DeadLetterRecord>();
    for (DeadLetterRecord failure : persistedWithContext) {
      recordsByCoordinate.put(DltCoordinate.from(failure), failure);
    }

    var result = new ArrayList<DeadLetterRecord>(failures.size());
    for (DeadLetterRecord failure : failures) {
      result.add(recordsByCoordinate.get(DltCoordinate.from(failure)));
    }
    return result;
  }

  private Map<DltCoordinate, DeadLetterRecord> batchUpsertRecordsInChunks(
      List<DeadLetterRecord> failures) {
    var persistedByCoordinate = new HashMap<DltCoordinate, DeadLetterRecord>();
    for (int start = 0; start < failures.size(); start += MAX_RECORDS_PER_BATCH_INSERT) {
      var end = Math.min(start + MAX_RECORDS_PER_BATCH_INSERT, failures.size());
      persistedByCoordinate.putAll(batchUpsertRecords(failures.subList(start, end)));
    }
    return persistedByCoordinate;
  }

  private Map<DltCoordinate, DeadLetterRecord> batchUpsertRecords(List<DeadLetterRecord> failures) {
    var sql =
        new StringBuilder("INSERT INTO processor.processor_dead_letter_record ")
            .append(RECORD_INSERT_COLUMNS)
            .append(" VALUES ");
    var parameters = new MapSqlParameterSource();
    for (int index = 0; index < failures.size(); index++) {
      if (index > 0) {
        sql.append(", ");
      }
      appendRecordValues(sql, parameters, failures.get(index), index);
    }
    sql.append(' ').append(RECORD_CONFLICT_CLAUSE);

    var records = jdbc.query(sql.toString(), parameters, (rs, _) -> recordFrom(rs));
    var byCoordinate = new HashMap<DltCoordinate, DeadLetterRecord>();
    for (DeadLetterRecord failure : records) {
      byCoordinate.put(DltCoordinate.from(failure), failure);
    }
    return byCoordinate;
  }

  private static void appendRecordValues(
      StringBuilder sql, MapSqlParameterSource parameters, DeadLetterRecord failure, int index) {
    sql.append(
        RECORD_INSERT_VALUES.formatted(
            named(PARAM_ID, index),
            named(PARAM_STATE, index),
            named(PARAM_STATE_UPDATED_AT, index),
            named(PARAM_UPDATED_AT, index),
            named(PARAM_STAGE, index),
            named(PARAM_FAILURE_KIND, index),
            named(PARAM_ORIGINAL_TOPIC, index),
            named(PARAM_ORIGINAL_PARTITION, index),
            named(PARAM_ORIGINAL_OFFSET, index),
            named(PARAM_DLT_TOPIC, index),
            named(PARAM_DLT_PARTITION, index),
            named(PARAM_DLT_OFFSET, index),
            named(PARAM_EXCEPTION_CLASS, index),
            named(PARAM_EXCEPTION_MESSAGE, index),
            named(PARAM_REPLAY_COUNT, index),
            named(PARAM_CONTEXT_JSON, index),
            named(PARAM_HEADERS_JSON, index),
            named(PARAM_PAYLOAD_REF, index),
            named(PARAM_PAYLOAD_SHA256, index),
            named(PARAM_PAYLOAD_SIZE_BYTES, index),
            named(PARAM_PAYLOAD_EXPIRES_AT, index),
            named(PARAM_INDEXED_AT, index)));
    parameters
        .addValue(namedValue(PARAM_ID, index), failure.id())
        .addValue(namedValue(PARAM_STATE, index), failure.state().name())
        .addValue(
            namedValue(PARAM_STATE_UPDATED_AT, index), offsetDateTime(failure.stateUpdatedAt()))
        .addValue(namedValue(PARAM_UPDATED_AT, index), offsetDateTime(failure.updatedAt()))
        .addValue(namedValue(PARAM_STAGE, index), failure.stage().name())
        .addValue(namedValue(PARAM_FAILURE_KIND, index), failure.failureKind().name())
        .addValue(namedValue(PARAM_ORIGINAL_TOPIC, index), failure.originalTopic())
        .addValue(namedValue(PARAM_ORIGINAL_PARTITION, index), failure.originalPartition())
        .addValue(namedValue(PARAM_ORIGINAL_OFFSET, index), failure.originalOffset())
        .addValue(namedValue(PARAM_DLT_TOPIC, index), failure.dltTopic())
        .addValue(namedValue(PARAM_DLT_PARTITION, index), failure.dltPartition())
        .addValue(namedValue(PARAM_DLT_OFFSET, index), failure.dltOffset())
        .addValue(namedValue(PARAM_EXCEPTION_CLASS, index), failure.exceptionClass())
        .addValue(namedValue(PARAM_EXCEPTION_MESSAGE, index), failure.exceptionMessage())
        .addValue(namedValue(PARAM_REPLAY_COUNT, index), failure.replayCount())
        .addValue(namedValue(PARAM_CONTEXT_JSON, index), failure.contextJson())
        .addValue(namedValue(PARAM_HEADERS_JSON, index), failure.headersJson())
        .addValue(namedValue(PARAM_PAYLOAD_REF, index), failure.payloadRef())
        .addValue(namedValue(PARAM_PAYLOAD_SHA256, index), failure.payloadSha256())
        .addValue(namedValue(PARAM_PAYLOAD_SIZE_BYTES, index), failure.payloadSizeBytes())
        .addValue(
            namedValue(PARAM_PAYLOAD_EXPIRES_AT, index), offsetDateTime(failure.payloadExpiresAt()))
        .addValue(namedValue(PARAM_INDEXED_AT, index), offsetDateTime(failure.indexedAt()));
  }

  private void batchUpsertAdminContexts(
      List<DeadLetterRecord> submitted,
      Map<DltCoordinate, DeadLetterRecord> persistedByCoordinate) {
    var sourceRows = new ArrayList<MapSqlParameterSource>();
    var jobRows = new ArrayList<MapSqlParameterSource>();
    var mapperRows = new ArrayList<MapSqlParameterSource>();
    var importRows = new ArrayList<MapSqlParameterSource>();
    for (DeadLetterRecord failure : submitted) {
      var persisted = persistedByCoordinate.get(DltCoordinate.from(failure));
      if (persisted == null || !persisted.id().equals(failure.id())) {
        continue;
      }
      collectAdminContextRows(
          failure.id(), failure.adminContext(), sourceRows, jobRows, mapperRows, importRows);
    }
    batchInsertContext(
        DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
        DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
        sourceRows);
    batchInsertContext(
        DeadLetterAdminContextSchema.QUALIFIED_JOB_CONTEXT_TABLE,
        DeadLetterAdminContextSchema.JOB_ID_COLUMN,
        jobRows);
    batchInsertContext(
        DeadLetterAdminContextSchema.QUALIFIED_MAPPER_CONTEXT_TABLE,
        DeadLetterAdminContextSchema.MAPPER_ID_COLUMN,
        mapperRows);
    batchInsertContext(
        DeadLetterAdminContextSchema.QUALIFIED_IMPORT_CONTEXT_TABLE,
        DeadLetterAdminContextSchema.IMPORT_ID_COLUMN,
        importRows);
  }

  private static void collectAdminContextRows(
      UUID failureId,
      DeadLetterRecord.AdminContext context,
      List<MapSqlParameterSource> sourceRows,
      List<MapSqlParameterSource> jobRows,
      List<MapSqlParameterSource> mapperRows,
      List<MapSqlParameterSource> importRows) {
    if (context == null) {
      return;
    }
    if (context.sourceId() != null) {
      sourceRows.add(
          contextRow(failureId, DeadLetterAdminContextSchema.SOURCE_ID_COLUMN, context.sourceId()));
    }
    if (context.jobId() != null) {
      jobRows.add(
          contextRow(failureId, DeadLetterAdminContextSchema.JOB_ID_COLUMN, context.jobId()));
    }
    if (context.mapperId() != null) {
      mapperRows.add(
          contextRow(failureId, DeadLetterAdminContextSchema.MAPPER_ID_COLUMN, context.mapperId()));
    }
    if (context.importId() != null) {
      importRows.add(
          contextRow(failureId, DeadLetterAdminContextSchema.IMPORT_ID_COLUMN, context.importId()));
    }
  }

  private static MapSqlParameterSource contextRow(UUID failureId, String valueColumn, UUID value) {
    return new MapSqlParameterSource()
        .addValue(DeadLetterAdminContextSchema.FAILURE_ID_COLUMN, failureId)
        .addValue(valueColumn, value);
  }

  private void batchInsertContext(
      String tableName, String valueColumn, List<MapSqlParameterSource> rows) {
    if (rows.isEmpty()) {
      return;
    }
    var sql =
        """
        INSERT INTO %s (failure_id, %s)
        VALUES (:failure_id, :%s)
        ON CONFLICT (failure_id) DO NOTHING
        """
            .formatted(tableName, valueColumn, valueColumn);
    jdbc.batchUpdate(sql, rows.toArray(MapSqlParameterSource[]::new));
  }

  @Transactional(readOnly = true)
  public Optional<DeadLetterRecord> findById(UUID failureId) {
    return Optional.ofNullable(entityManager.find(ProcessorDeadLetterRecordEntity.class, failureId))
        .map(ProcessorDeadLetterRecordEntity::toRecord)
        .map(this::attachAdminContext);
  }

  @Transactional(readOnly = true)
  public Optional<DeadLetterRecord> findByDltCoordinates(
      String dltTopic, int dltPartition, long dltOffset) {
    try {
      var failure =
          entityManager
              .createQuery(
                  """
                  select failure
                    from ProcessorDeadLetterRecordEntity failure
                   where failure.dltTopic = :dltTopic
                     and failure.dltPartition = :dltPartition
                     and failure.dltOffset = :dltOffset
                  """,
                  ProcessorDeadLetterRecordEntity.class)
              .setParameter(PARAM_DLT_TOPIC, dltTopic)
              .setParameter(PARAM_DLT_PARTITION, dltPartition)
              .setParameter(PARAM_DLT_OFFSET, dltOffset)
              .getSingleResult()
              .toRecord();
      return Optional.of(attachAdminContext(failure));
    } catch (NoResultException _) {
      return Optional.empty();
    }
  }

  @Transactional(readOnly = true)
  public Map<DltCoordinate, DeadLetterRecord> findByDltCoordinates(
      List<DltCoordinate> coordinates) {
    if (coordinates.isEmpty()) {
      return Map.of();
    }
    var uniqueCoordinates = coordinates.stream().distinct().toList();
    var sql =
        new StringBuilder(
            """
            SELECT *
              FROM processor.processor_dead_letter_record
             WHERE (dlt_topic, dlt_partition, dlt_offset) IN (
            """);
    var parameters = new MapSqlParameterSource();
    for (int index = 0; index < uniqueCoordinates.size(); index++) {
      if (index > 0) {
        sql.append(", ");
      }
      var coordinate = uniqueCoordinates.get(index);
      sql.append("(")
          .append(named("topic", index))
          .append(", ")
          .append(named("partition", index))
          .append(", ")
          .append(named("offset", index))
          .append(")");
      parameters
          .addValue(namedValue("topic", index), coordinate.topic())
          .addValue(namedValue("partition", index), coordinate.partition())
          .addValue(namedValue("offset", index), coordinate.offset());
    }
    sql.append(")");

    var records =
        attachAdminContexts(jdbc.query(sql.toString(), parameters, (rs, _) -> recordFrom(rs)));
    var byCoordinate = new HashMap<DltCoordinate, DeadLetterRecord>();
    for (DeadLetterRecord failure : records) {
      byCoordinate.put(DltCoordinate.from(failure), failure);
    }
    return byCoordinate;
  }

  @Transactional(readOnly = true)
  public long count() {
    return entityManager
        .createQuery(
            "select count(failure) from ProcessorDeadLetterRecordEntity failure", Long.class)
        .getSingleResult();
  }

  private void upsertAdminContext(UUID failureId, DeadLetterRecord.AdminContext context) {
    if (context == null) {
      return;
    }
    if (context.sourceId() != null) {
      persistIfAbsent(
          ProcessorDeadLetterSourceContextEntity.class,
          failureId,
          new ProcessorDeadLetterSourceContextEntity(failureId, context.sourceId()));
    }
    if (context.jobId() != null) {
      persistIfAbsent(
          ProcessorDeadLetterJobContextEntity.class,
          failureId,
          new ProcessorDeadLetterJobContextEntity(failureId, context.jobId()));
    }
    if (context.mapperId() != null) {
      persistIfAbsent(
          ProcessorDeadLetterMapperContextEntity.class,
          failureId,
          new ProcessorDeadLetterMapperContextEntity(failureId, context.mapperId()));
    }
    if (context.importId() != null) {
      persistIfAbsent(
          ProcessorDeadLetterImportContextEntity.class,
          failureId,
          new ProcessorDeadLetterImportContextEntity(failureId, context.importId()));
    }
  }

  private DeadLetterRecord attachAdminContext(DeadLetterRecord failure) {
    return failure.withAdminContext(
        new DeadLetterRecord.AdminContext(
            sourceContextId(failure.id()),
            jobContextId(failure.id()),
            mapperContextId(failure.id()),
            importContextId(failure.id())));
  }

  private List<DeadLetterRecord> attachAdminContexts(List<DeadLetterRecord> failures) {
    if (failures.isEmpty()) {
      return List.of();
    }
    var ids = failures.stream().map(DeadLetterRecord::id).toList();
    var sourceIds =
        contextIds(
            DeadLetterAdminContextSchema.QUALIFIED_SOURCE_CONTEXT_TABLE,
            DeadLetterAdminContextSchema.SOURCE_ID_COLUMN,
            ids);
    var jobIds =
        contextIds(
            DeadLetterAdminContextSchema.QUALIFIED_JOB_CONTEXT_TABLE,
            DeadLetterAdminContextSchema.JOB_ID_COLUMN,
            ids);
    var mapperIds =
        contextIds(
            DeadLetterAdminContextSchema.QUALIFIED_MAPPER_CONTEXT_TABLE,
            DeadLetterAdminContextSchema.MAPPER_ID_COLUMN,
            ids);
    var importIds =
        contextIds(
            DeadLetterAdminContextSchema.QUALIFIED_IMPORT_CONTEXT_TABLE,
            DeadLetterAdminContextSchema.IMPORT_ID_COLUMN,
            ids);
    var attached = new ArrayList<DeadLetterRecord>(failures.size());
    for (DeadLetterRecord failure : failures) {
      attached.add(
          failure.withAdminContext(
              new DeadLetterRecord.AdminContext(
                  sourceIds.get(failure.id()),
                  jobIds.get(failure.id()),
                  mapperIds.get(failure.id()),
                  importIds.get(failure.id()))));
    }
    return attached;
  }

  private Map<UUID, UUID> contextIds(String tableName, String valueColumn, List<UUID> failureIds) {
    var sql =
        """
        SELECT failure_id, %s
          FROM %s
         WHERE failure_id IN (:failureIds)
        """
            .formatted(valueColumn, tableName);
    var parameters = new MapSqlParameterSource("failureIds", failureIds);
    return jdbc.query(
        sql,
        parameters,
        rs -> {
          var values = new HashMap<UUID, UUID>();
          while (rs.next()) {
            values.put(
                rs.getObject(DeadLetterAdminContextSchema.FAILURE_ID_COLUMN, UUID.class),
                rs.getObject(valueColumn, UUID.class));
          }
          return values;
        });
  }

  private static DeadLetterRecord recordFrom(ResultSet rs) throws SQLException {
    return new DeadLetterRecord(
        rs.getObject("id", UUID.class),
        DeadLetterRecord.State.valueOf(rs.getString(PARAM_STATE)),
        instant(rs, "state_updated_at"),
        instant(rs, "updated_at"),
        DeadLetterRecord.Stage.valueOf(rs.getString(PARAM_STAGE)),
        DeadLetterRecord.FailureKind.valueOf(rs.getString("failure_kind")),
        rs.getString("original_topic"),
        integerOrNull(rs, "original_partition"),
        longOrNull(rs, "original_offset"),
        rs.getString("dlt_topic"),
        rs.getInt("dlt_partition"),
        rs.getLong("dlt_offset"),
        rs.getString("exception_class"),
        rs.getString("exception_message"),
        rs.getInt("replay_count"),
        rs.getString("context_json"),
        rs.getString("headers_json"),
        rs.getString("payload_ref"),
        rs.getString("payload_sha256"),
        longOrNull(rs, "payload_size_bytes"),
        instant(rs, "payload_expires_at"),
        instant(rs, "indexed_at"),
        DeadLetterRecord.AdminContext.empty());
  }

  private static Integer integerOrNull(ResultSet rs, String columnName) throws SQLException {
    var value = rs.getInt(columnName);
    return rs.wasNull() ? null : value;
  }

  private static Long longOrNull(ResultSet rs, String columnName) throws SQLException {
    var value = rs.getLong(columnName);
    return rs.wasNull() ? null : value;
  }

  private static OffsetDateTime offsetDateTime(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String columnName) throws SQLException {
    return rs.getObject(columnName, OffsetDateTime.class).toInstant();
  }

  private static String named(String baseName, int index) {
    return ":" + namedValue(baseName, index);
  }

  private static String namedValue(String baseName, int index) {
    return baseName + index;
  }

  public record DltCoordinate(String topic, int partition, long offset) {

    static DltCoordinate from(DeadLetterRecord failure) {
      return new DltCoordinate(failure.dltTopic(), failure.dltPartition(), failure.dltOffset());
    }
  }

  private <T> void persistIfAbsent(Class<T> entityType, UUID failureId, T entity) {
    if (entityManager.find(entityType, failureId) == null) {
      entityManager.persist(entity);
    }
  }

  private UUID sourceContextId(UUID failureId) {
    var context = entityManager.find(ProcessorDeadLetterSourceContextEntity.class, failureId);
    return context == null ? null : context.getSourceId();
  }

  private UUID jobContextId(UUID failureId) {
    var context = entityManager.find(ProcessorDeadLetterJobContextEntity.class, failureId);
    return context == null ? null : context.getJobId();
  }

  private UUID mapperContextId(UUID failureId) {
    var context = entityManager.find(ProcessorDeadLetterMapperContextEntity.class, failureId);
    return context == null ? null : context.getMapperId();
  }

  private UUID importContextId(UUID failureId) {
    var context = entityManager.find(ProcessorDeadLetterImportContextEntity.class, failureId);
    return context == null ? null : context.getImportId();
  }
}
