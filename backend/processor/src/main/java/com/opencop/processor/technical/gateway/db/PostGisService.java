// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.processor.ports.technical.gateway.db.GeoJsonRepository;
import com.opencop.processor.technical.gateway.model.PostGisFeatureRecord;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.geojson.Feature;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

public class PostGisService implements GeoJsonRepository<Feature>, PostGisFeatureRecordWriter {

  static final int HELPER_EXECUTION_CHUNK_SIZE = 100;

  private static final Comparator<HelperRecord> HELPER_RECORD_COMPARATOR =
      Comparator.comparing(
              HelperRecord::playbackAt, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(HelperRecord::eventId, Comparator.nullsFirst(Comparator.naturalOrder()));

  private static final Comparator<HelperRecord> HELPER_LOCK_ORDER_COMPARATOR =
      Comparator.comparing(HelperRecord::source, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparing(HelperRecord::objectId, Comparator.nullsFirst(Comparator.naturalOrder()));

  private final DataSource dataSource;
  private final PostGisFeatureRecordMapper postGisFeatureRecordMapper;

  // TODO: Consider migrating targeted PostGIS batch persistence operations to JPA when this
  // repository is refactored more broadly.
  private static final String SQL =
      """
                INSERT INTO geo.features
                  (id, feature_kind, source, author_id, object_id, created_at, start_at, stale_at, geom, payload,
                   source_kind, mapper_id, mapper_version, ingress_id, mapping_execution_id,
                   source_received_at, mapping_completed_at)
                VALUES
                  (?, ?, ?, ?, ?, now(), ?, ?, public.ST_Force3D(public.ST_SetSRID(public.ST_GeomFromGeoJSON(?),4326)), ?::jsonb,
                   ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  feature_kind = EXCLUDED.feature_kind,
                  source       = EXCLUDED.source,
                  author_id    = EXCLUDED.author_id,
                  object_id    = COALESCE(EXCLUDED.object_id, geo.features.object_id),
                  start_at     = EXCLUDED.start_at,
                  stale_at     = EXCLUDED.stale_at,
                  geom         = EXCLUDED.geom,
                  payload      = EXCLUDED.payload,
                  -- Provenance is preserved on null, the way object_id above is: a duplicate
                  -- arriving without provenance headers - a legacy producer, or a replay through a
                  -- path that does not carry them - must not erase what is already known. Null
                  -- here means "not reported", never "known to be absent".
                  source_kind          = COALESCE(EXCLUDED.source_kind, geo.features.source_kind),
                  mapper_id            = COALESCE(EXCLUDED.mapper_id, geo.features.mapper_id),
                  mapper_version       = COALESCE(EXCLUDED.mapper_version, geo.features.mapper_version),
                  ingress_id           = COALESCE(EXCLUDED.ingress_id, geo.features.ingress_id),
                  mapping_execution_id = COALESCE(EXCLUDED.mapping_execution_id, geo.features.mapping_execution_id),
                  source_received_at   = COALESCE(EXCLUDED.source_received_at, geo.features.source_received_at),
                  mapping_completed_at = COALESCE(EXCLUDED.mapping_completed_at, geo.features.mapping_completed_at)
                 """;

  // `first_emission_at` updates with LEAST so out-of-order older events lower
  // it correctly; the rest of the columns are still gated by the
  // `EXCLUDED.playback_at >= existing.playback_at` predicate so a late older
  // event doesn't clobber the latest known position/stale_at.
  private static final String HELPER_SQL =
      """
                INSERT INTO geo.feature_latest_active_helper
                  (source, object_id, event_id, playback_at, first_emission_at, created_at, stale_at)
                VALUES
                  (?, ?, ?, ?, ?, now(), ?)
                ON CONFLICT (source, object_id) DO UPDATE SET
                  event_id = CASE
                    WHEN EXCLUDED.playback_at >= geo.feature_latest_active_helper.playback_at
                    THEN EXCLUDED.event_id
                    ELSE geo.feature_latest_active_helper.event_id
                  END,
                  playback_at = GREATEST(geo.feature_latest_active_helper.playback_at, EXCLUDED.playback_at),
                  first_emission_at = LEAST(geo.feature_latest_active_helper.first_emission_at, EXCLUDED.first_emission_at),
                  created_at = CASE
                    WHEN EXCLUDED.playback_at >= geo.feature_latest_active_helper.playback_at
                    THEN EXCLUDED.created_at
                    ELSE geo.feature_latest_active_helper.created_at
                  END,
                  stale_at = CASE
                    WHEN EXCLUDED.playback_at >= geo.feature_latest_active_helper.playback_at
                    THEN EXCLUDED.stale_at
                    ELSE geo.feature_latest_active_helper.stale_at
                  END
                WHERE EXCLUDED.playback_at >= geo.feature_latest_active_helper.playback_at
                   OR EXCLUDED.first_emission_at < geo.feature_latest_active_helper.first_emission_at
                """;

  public PostGisService(
      final DataSource dataSource, final PostGisFeatureRecordMapper postGisFeatureRecordMapper) {
    this.dataSource = dataSource;
    this.postGisFeatureRecordMapper = postGisFeatureRecordMapper;
  }

  @Override
  @Transactional
  public void batchPersist(List<Feature> features) throws RepositoryException {
    List<PostGisFeatureRecord> records = new ArrayList<>(features.size());
    for (var feature : features) {
      records.add(postGisFeatureRecordMapper.toRecord(feature));
    }
    save(records);
  }

  @Override
  @Transactional
  // TODO: Consider migrating targeted PostGIS batch persistence operations to JPA when this
  // repository is refactored more broadly.
  public void save(List<PostGisFeatureRecord> featureRecords, String batchKey)
      throws RepositoryException {
    if (featureRecords.isEmpty()) {
      return;
    }

    List<List<HelperRecord>> helperRecordExecutionGroups =
        helperRecordExecutionGroupsFor(featureRecords);

    var connection = DataSourceUtils.getConnection(dataSource);
    try (var ps = connection.prepareStatement(SQL);
        var helperPs = connection.prepareStatement(HELPER_SQL)) {
      for (var r : featureRecords) {
        ps.setObject(1, r.id());
        ps.setString(2, r.kind());
        ps.setObject(3, r.source());
        ps.setObject(4, r.authorId());
        ps.setObject(5, r.objectId());
        ps.setObject(6, toUtc(r.startAt()));
        ps.setObject(7, toUtc(r.staleAt()));
        ps.setString(8, r.geometryJson());
        ps.setString(9, r.payloadJson());
        var provenance = r.provenance();
        ps.setString(10, provenance.sourceKind() == null ? null : provenance.sourceKind().name());
        ps.setObject(11, provenance.mapperId());
        ps.setObject(12, provenance.mapperVersion());
        ps.setString(13, provenance.ingressId());
        ps.setString(14, provenance.mappingExecutionId());
        ps.setObject(15, toUtc(provenance.sourceReceivedAt()));
        ps.setObject(16, toUtc(provenance.mappingCompletedAt()));
        ps.addBatch();
      }

      ps.executeBatch(); // commit/rollback handled by Spring

      for (var helperRecordExecutionGroup : helperRecordExecutionGroups) {
        for (var helperRecord : helperRecordExecutionGroup) {
          helperPs.setObject(1, helperRecord.source());
          helperPs.setString(2, helperRecord.objectId());
          helperPs.setObject(3, helperRecord.eventId());
          helperPs.setObject(4, helperRecord.playbackAt());
          helperPs.setObject(5, helperRecord.firstEmissionAt());
          helperPs.setObject(6, helperRecord.staleAt());
          helperPs.addBatch();
        }
        helperPs.executeBatch();
        helperPs.clearBatch();
      }

      // Same connection, so the same transaction: a rolled-back batch must not leave a resource
      // reporting activity for events that were never stored. Last, so the caller can commit
      // without holding shared projection locks behind the feature and helper work.
      PostGisActivityWriter.record(connection, batchKey, featureRecords);
    } catch (SQLException e) {
      var repositoryException = new RepositoryException(RepositoryExceptionCodes.FAILED_TO_PERSIST);
      repositoryException.initCause(e);
      throw repositoryException;
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  private OffsetDateTime toUtc(Instant i) {
    return toUtcValue(i);
  }

  static List<HelperRecord> helperRecordsFor(List<PostGisFeatureRecord> featureRecords) {
    Map<HelperRecordKey, HelperRecord> helperRecordsByKey = new LinkedHashMap<>();

    for (var record : featureRecords) {
      if (record.objectId() == null) {
        continue;
      }

      OffsetDateTime emissionAt = toUtcValue(record.startAt());
      HelperRecord candidate =
          new HelperRecord(
              record.source(),
              record.objectId(),
              record.id(),
              emissionAt,
              emissionAt,
              toUtcValue(record.staleAt()));
      HelperRecordKey key = new HelperRecordKey(candidate.source(), candidate.objectId());
      helperRecordsByKey.merge(
          key,
          candidate,
          (existing, incoming) -> {
            HelperRecord winner =
                HELPER_RECORD_COMPARATOR.compare(existing, incoming) >= 0 ? existing : incoming;
            // first_emission_at = MIN across all candidates in the batch.
            OffsetDateTime earliest =
                earliestEmission(existing.firstEmissionAt(), incoming.firstEmissionAt());
            return new HelperRecord(
                winner.source(),
                winner.objectId(),
                winner.eventId(),
                winner.playbackAt(),
                earliest,
                winner.staleAt());
          });
    }

    return helperRecordsByKey.values().stream().sorted(HELPER_LOCK_ORDER_COMPARATOR).toList();
  }

  static List<List<HelperRecord>> helperRecordExecutionGroupsFor(
      List<PostGisFeatureRecord> featureRecords) {
    List<HelperRecord> helperRecords = helperRecordsFor(featureRecords);
    List<List<HelperRecord>> groups = new ArrayList<>();

    for (int start = 0; start < helperRecords.size(); start += HELPER_EXECUTION_CHUNK_SIZE) {
      int end = Math.min(start + HELPER_EXECUTION_CHUNK_SIZE, helperRecords.size());
      groups.add(helperRecords.subList(start, end));
    }

    return groups;
  }

  private static OffsetDateTime toUtcValue(Instant i) {
    return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
  }

  private static OffsetDateTime earliestEmission(OffsetDateTime a, OffsetDateTime b) {
    if (a == null) return b;
    if (b == null) return a;
    return a.isBefore(b) ? a : b;
  }

  static record HelperRecord(
      UUID source,
      String objectId,
      UUID eventId,
      OffsetDateTime playbackAt,
      OffsetDateTime firstEmissionAt,
      OffsetDateTime staleAt) {}

  private record HelperRecordKey(UUID source, String objectId) {}
}
