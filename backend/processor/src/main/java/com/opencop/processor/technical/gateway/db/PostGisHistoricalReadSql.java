// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.business.model.history.HistoricalReadCursor;

final class PostGisHistoricalReadSql {

  static final String IS_RESIDUAL_COLUMN = "is_residual";

  static final String WINNERS_SQL =
      """
      WITH winners AS (
        SELECT DISTINCT ON (source, object_id)
          id,
          source,
          object_id,
          created_at,
          start_at,
          stale_at,
          COALESCE(start_at, created_at) AS playback_at
        FROM geo.features
        WHERE source IN (:sourceIds)
          AND COALESCE(start_at, created_at) < :to
          AND (stale_at IS NULL OR stale_at > :from)
        ORDER BY source, object_id, COALESCE(start_at, created_at) DESC, created_at DESC, id DESC
      )
      SELECT
        winners.id,
        winners.source,
        winners.object_id,
        winners.created_at,
        winners.start_at,
        winners.stale_at,
        payloads.payload::text AS payload_json,
        winners.playback_at
      FROM winners
      JOIN geo.features payloads ON payloads.id = winners.id
      """;

  /**
   * Streaming SQL for single-source helper snapshots. Stable rows come from
   * `geo.feature_latest_active_helper`; residual rows resolve latest-before-to metadata first and
   * join payloads only for surviving per-object winners.
   */
  static final String HELPER_WINNERS_SQL =
      """
      WITH stable_winners AS (
        SELECT
          helper.event_id AS id,
          helper.source,
          helper.object_id,
          helper.created_at,
          NULL::timestamptz AS start_at,
          helper.stale_at,
          features.payload::text AS payload_json,
          helper.playback_at,
          false AS %s
        FROM geo.feature_latest_active_helper helper
        JOIN geo.features features ON features.id = helper.event_id
        WHERE helper.source = :sourceId
          AND helper.first_emission_at < :to
          AND (helper.stale_at IS NULL OR helper.stale_at > :from)
          AND helper.playback_at < :from
      ),
      residual_metadata AS (
        SELECT
          latest.id,
          latest.source,
          latest.object_id,
          latest.created_at,
          latest.start_at,
          latest.stale_at,
          latest.playback_at
        FROM geo.feature_latest_active_helper helper
        JOIN LATERAL (
          SELECT
            features.id,
            features.source,
            features.object_id,
            features.created_at,
            features.start_at,
            features.stale_at,
            COALESCE(features.start_at, features.created_at) AS playback_at
          FROM geo.features features
          WHERE features.source = :sourceId
            AND features.object_id = helper.object_id
            AND COALESCE(features.start_at, features.created_at) < :to
          ORDER BY COALESCE(features.start_at, features.created_at) DESC,
                   features.created_at DESC,
                   features.id DESC
          LIMIT 1
        ) latest ON true
        WHERE helper.source = :sourceId
          AND helper.first_emission_at < :to
          AND (helper.stale_at IS NULL OR helper.stale_at > :from)
          AND helper.playback_at >= :from
          AND (latest.stale_at IS NULL OR latest.stale_at > :from)
      ),
      residual_winners AS (
        SELECT
          residual_metadata.id,
          residual_metadata.source,
          residual_metadata.object_id,
          residual_metadata.created_at,
          residual_metadata.start_at,
          residual_metadata.stale_at,
          payloads.payload::text AS payload_json,
          residual_metadata.playback_at,
          true AS %s
        FROM residual_metadata
        JOIN geo.features payloads ON payloads.id = residual_metadata.id
      )
      SELECT
        id,
        source,
        object_id,
        created_at,
        start_at,
        stale_at,
        payload_json,
        playback_at,
        %s
      FROM (
        SELECT
          id,
          source,
          object_id,
          created_at,
          start_at,
          stale_at,
          payload_json,
          playback_at,
          %s
        FROM stable_winners
        UNION ALL
        SELECT
          id,
          source,
          object_id,
          created_at,
          start_at,
          stale_at,
          payload_json,
          playback_at,
          %s
        FROM residual_winners
      ) all_rows
      """
          .formatted(
              IS_RESIDUAL_COLUMN,
              IS_RESIDUAL_COLUMN,
              IS_RESIDUAL_COLUMN,
              IS_RESIDUAL_COLUMN,
              IS_RESIDUAL_COLUMN);

  static final String STREAM_HELPER_WINNERS_SQL = HELPER_WINNERS_SQL;

  static final String CHECKPOINT_SQL =
      """
      SELECT
        checkpoint.event_id AS id,
        checkpoint.source,
        checkpoint.object_id,
        checkpoint.created_at,
        checkpoint.playback_at AS start_at,
        checkpoint.stale_at,
        payloads.payload::text AS payload_json,
        checkpoint.playback_at
      FROM geo.feature_history_checkpoint checkpoint
      JOIN geo.features payloads ON payloads.id = checkpoint.event_id
      WHERE checkpoint.source = :sourceId
        AND checkpoint.resolution = :checkpointResolution
        AND checkpoint.checkpoint_at = (
          SELECT MAX(candidate.checkpoint_at)
          FROM geo.feature_history_checkpoint candidate
          WHERE candidate.source = :sourceId
            AND candidate.resolution = :checkpointResolution
            AND candidate.checkpoint_at <= :checkpointAt
        )
      """;

  static final String DELTA_SQL =
      """
      WITH delta_rows AS (
        SELECT
          id,
          source,
          object_id,
          created_at,
          start_at,
          stale_at,
          payload::text AS payload_json,
          COALESCE(start_at, created_at) AS playback_at
        FROM geo.features
        WHERE source IN (:sourceIds)
          AND COALESCE(start_at, created_at) >= :from
          AND COALESCE(start_at, created_at) < :to
      )
      SELECT
        delta_rows.id,
        delta_rows.source,
        delta_rows.object_id,
        delta_rows.created_at,
        delta_rows.start_at,
        delta_rows.stale_at,
        delta_rows.payload_json,
        delta_rows.playback_at
      FROM delta_rows
      """;

  /**
   * State-reuse delta path. It intentionally returns only the latest row per (source, object_id)
   * within the delta window because the state accumulator overwrites by that key. Full event
   * consumers must continue using DELTA_SQL.
   */
  static final String STATE_DELTA_SQL =
      """
      WITH changed_objects AS MATERIALIZED (
        SELECT DISTINCT
          features.source,
          features.object_id
        FROM geo.features features
        WHERE features.source IN (:sourceIds)
          AND features.start_at >= :from
          AND features.start_at < :to
      )
      SELECT
        winners.id,
        winners.source,
        winners.object_id,
        winners.created_at,
        winners.start_at,
        winners.stale_at,
        payloads.payload::text AS payload_json,
        winners.playback_at
      FROM changed_objects
      JOIN LATERAL (
        SELECT
          features.id,
          features.source,
          features.object_id,
          features.created_at,
          features.start_at,
          features.stale_at,
          COALESCE(features.start_at, features.created_at) AS playback_at
        FROM geo.features features
        WHERE features.source = changed_objects.source
          AND features.object_id = changed_objects.object_id
          AND COALESCE(features.start_at, features.created_at) >= :from
          AND COALESCE(features.start_at, features.created_at) < :to
        ORDER BY COALESCE(features.start_at, features.created_at) DESC,
                 features.created_at DESC,
                 features.id DESC
        LIMIT 1
      ) winners ON true
      JOIN geo.features payloads ON payloads.id = winners.id
      """;

  static final String HELPER_DOMAIN_STATE_DELTA_SQL =
      """
      WITH helper_domain AS MATERIALIZED (
        SELECT helper.object_id
        FROM geo.feature_latest_active_helper helper
        WHERE helper.source = :sourceId
      ),
      winners AS (
        SELECT
          features.id,
          features.source,
          features.object_id,
          features.created_at,
          features.start_at,
          features.stale_at,
          COALESCE(features.start_at, features.created_at) AS playback_at
        FROM helper_domain helper
        JOIN LATERAL (
          SELECT
            features.id,
            features.source,
            features.object_id,
            features.created_at,
            features.start_at,
            features.stale_at
          FROM geo.features features
          WHERE features.source = :sourceId
            AND features.object_id = helper.object_id
            AND COALESCE(features.start_at, features.created_at) >= :from
            AND COALESCE(features.start_at, features.created_at) < :to
          ORDER BY COALESCE(features.start_at, features.created_at) DESC,
                   features.created_at DESC,
                   features.id DESC
          LIMIT 1
        ) features ON true
      )
      SELECT
        winners.id,
        winners.source,
        winners.object_id,
        winners.created_at,
        winners.start_at,
        winners.stale_at,
        payloads.payload::text AS payload_json,
        winners.playback_at
      FROM winners
      JOIN geo.features payloads ON payloads.id = winners.id
      """;

  private static final String STREAM_DELTA_ORDER_SQL =
      """
      ORDER BY delta_rows.playback_at ASC, delta_rows.object_id ASC, delta_rows.id ASC
      """;

  private static final String STREAM_STATE_DELTA_ORDER_SQL =
      """
      ORDER BY winners.playback_at ASC, winners.object_id ASC, winners.id ASC
      """;

  static final String STREAM_HELPER_WINNERS_ORDER_SQL =
      "ORDER BY all_rows.playback_at ASC, all_rows.object_id ASC, all_rows.id ASC";

  private static final String CURSOR_SQL =
      """
        WHERE (
          %splayback_at > :cursorPlaybackAt
          OR (
            %splayback_at = :cursorPlaybackAt
            AND (
              %sobject_id > :cursorObjectId
              OR (%sobject_id = :cursorObjectId AND %sid > :cursorEventId)
            )
          )
        )
      """;

  private static final String ORDER_SQL =
      """
      ORDER BY %splayback_at ASC, %sobject_id ASC, %sid ASC
      LIMIT :limit
      """;

  private PostGisHistoricalReadSql() {}

  static String winnersSql(HistoricalReadCursor cursor) {
    return WINNERS_SQL
        + (cursor == null ? "" : qualifiedCursorSql("winners"))
        + qualifiedOrderSql("winners");
  }

  static String deltaSql(HistoricalReadCursor cursor) {
    return DELTA_SQL
        + (cursor == null ? "" : qualifiedCursorSql("delta_rows"))
        + qualifiedOrderSql("delta_rows");
  }

  static String streamDeltaSql() {
    return DELTA_SQL + "\n" + STREAM_DELTA_ORDER_SQL;
  }

  static String streamStateDeltaSql() {
    return STATE_DELTA_SQL + "\n" + STREAM_STATE_DELTA_ORDER_SQL;
  }

  static String streamHelperDomainStateDeltaSql() {
    return HELPER_DOMAIN_STATE_DELTA_SQL + "\n" + STREAM_STATE_DELTA_ORDER_SQL;
  }

  static String helperWinnersSql(HistoricalReadCursor cursor) {
    return HELPER_WINNERS_SQL
        + (cursor == null ? "" : qualifiedCursorSql("all_rows"))
        + STREAM_HELPER_WINNERS_ORDER_SQL
        + "\nLIMIT :limit\n";
  }

  static String streamHelperWinnersSql(HistoricalReadCursor cursor) {
    return STREAM_HELPER_WINNERS_SQL
        + (cursor == null ? "" : qualifiedCursorSql("all_rows"))
        + STREAM_HELPER_WINNERS_ORDER_SQL
        + "\n";
  }

  private static String qualifiedCursorSql(String alias) {
    String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
    return CURSOR_SQL.formatted(prefix, prefix, prefix, prefix, prefix);
  }

  private static String qualifiedOrderSql(String alias) {
    String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
    return ORDER_SQL.formatted(prefix, prefix, prefix);
  }
}
