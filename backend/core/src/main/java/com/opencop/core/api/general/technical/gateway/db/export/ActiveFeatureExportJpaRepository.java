// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveFeatureExportJpaRepository
    extends JpaRepository<GeoFeatureExportEntity, UUID> {

  @Query(
      value =
          """
          SELECT features.id AS id,
                 features.source AS "sourceId",
                 'SOURCE' AS "sourceType",
                 features.object_id AS "objectId",
                 features.payload::text AS payload
          FROM geo.feature_latest_active_helper helper
          JOIN geo.features features
            ON features.source = helper.source
           AND features.object_id = helper.object_id
           AND features.id = helper.event_id
          WHERE helper.source = :sourceId
            AND (helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP)
          ORDER BY helper.object_id
          """,
      nativeQuery = true)
  List<ActiveFeatureExportProjection> findSourceRows(@Param("sourceId") UUID sourceId);

  @Query(
      value =
          """
          SELECT features.id AS id,
                 features.source AS "sourceId",
                 'SOURCE' AS "sourceType",
                 features.object_id AS "objectId",
                 features.payload::text AS payload
          FROM geo.feature_latest_active_helper helper
          JOIN geo.features features
            ON features.source = helper.source
           AND features.object_id = helper.object_id
           AND features.id = helper.event_id
          WHERE helper.source IN (:sourceIds)
            AND (helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP)
          ORDER BY features.source, helper.object_id
          """,
      nativeQuery = true)
  List<ActiveFeatureExportProjection> findSourceRowsBySourceIds(
      @Param("sourceIds") List<UUID> sourceIds);

  @Query(
      value =
          """
          SELECT features.id AS id,
                 features.source AS "sourceId",
                 CASE
                   WHEN helper.source = :layerId THEN 'LAYER'
                   ELSE 'SOURCE'
                 END AS "sourceType",
                 features.object_id AS "objectId",
                 features.payload::text AS payload
          FROM geo.feature_latest_active_helper helper
          JOIN geo.features features
            ON features.source = helper.source
           AND features.object_id = helper.object_id
           AND features.id = helper.event_id
          WHERE (helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP)
            AND (
              helper.source = :layerId
              OR (:includeTransitiveData
              AND EXISTS (
                SELECT 1
                FROM iam.server_layer_access access
                JOIN profiles.data_source source
                  ON source.id = access.server_id
                WHERE access.server_id = helper.source
                  AND access.layer_id = :layerId
                  AND source.archived_at IS NULL
              ))
            )
          ORDER BY features.source, helper.object_id
          """,
      nativeQuery = true)
  List<ActiveFeatureExportProjection> findLayerRows(
      @Param("layerId") UUID layerId,
      @Param("includeTransitiveData") boolean includeTransitiveData);

  @Query(
      value =
          """
          SELECT features.id AS id,
                 features.source AS "sourceId",
                 CASE
                   WHEN helper.source IN (:layerIds) THEN 'LAYER'
                   ELSE 'SOURCE'
                 END AS "sourceType",
                 features.object_id AS "objectId",
                 features.payload::text AS payload
          FROM geo.feature_latest_active_helper helper
          JOIN geo.features features
            ON features.source = helper.source
           AND features.object_id = helper.object_id
           AND features.id = helper.event_id
          WHERE (helper.stale_at IS NULL OR helper.stale_at > CURRENT_TIMESTAMP)
            AND (
              helper.source IN (:layerIds)
              OR EXISTS (
                SELECT 1
                FROM iam.server_layer_access access
                JOIN profiles.data_source source
                  ON source.id = access.server_id
                WHERE access.server_id = helper.source
                  AND access.layer_id IN (:transitiveLayerIds)
                  AND source.archived_at IS NULL
              )
            )
          ORDER BY features.source, helper.object_id
          """,
      nativeQuery = true)
  List<ActiveFeatureExportProjection> findLayerRowsByLayerSubjects(
      @Param("layerIds") List<UUID> layerIds,
      @Param("transitiveLayerIds") List<UUID> transitiveLayerIds);

  @Query(
      value =
          """
          SELECT features.id AS id,
                 features.source AS "sourceId",
                 CASE
                   WHEN EXISTS (
                     SELECT 1
                     FROM iam.layer layer_subject
                     WHERE layer_subject.id = features.source
                   ) THEN 'LAYER'
                   ELSE 'SOURCE'
                 END AS "sourceType",
                 features.object_id AS "objectId",
                 features.payload::text AS payload
          FROM geo.features features
          WHERE features.id IN (:elementIds)
          ORDER BY features.source, features.object_id, features.created_at DESC, features.id
          """,
      nativeQuery = true)
  List<ActiveFeatureExportProjection> findElementRowsByIds(
      @Param("elementIds") List<UUID> elementIds);
}
