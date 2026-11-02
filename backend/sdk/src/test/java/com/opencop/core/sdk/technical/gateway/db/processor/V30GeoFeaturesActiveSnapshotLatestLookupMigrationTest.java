// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V30GeoFeaturesActiveSnapshotLatestLookupMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void migratedGeoFeatures_hasPlaybackLatestLookupIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "29");
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<IndexColumn> columns =
          jdbc.query(
              """
              WITH target_index AS (
                SELECT idx.indexrelid, idx.indnatts, idx.indnkeyatts, idx.indoption
                FROM pg_index idx
                JOIN pg_class index_class ON index_class.oid = idx.indexrelid
                JOIN pg_namespace index_namespace
                  ON index_namespace.oid = index_class.relnamespace
                JOIN pg_class table_class ON table_class.oid = idx.indrelid
                JOIN pg_namespace table_namespace
                  ON table_namespace.oid = table_class.relnamespace
                WHERE index_namespace.nspname = 'geo'
                  AND index_class.relname = 'geofeatures_source_object_playback_latest_idx'
                  AND table_namespace.nspname = 'geo'
                  AND table_class.relname = 'features'
              )
              SELECT ordinal_position,
                     pg_get_indexdef(indexrelid, ordinal_position, true) AS column_definition,
                     (indoption[ordinal_position - 1] & 1) = 1 AS descending,
                     ordinal_position > indnkeyatts AS included
              FROM target_index
              CROSS JOIN generate_series(1, indnatts) ordinal_position
              ORDER BY ordinal_position
              """,
              (rs, rowNum) ->
                  new IndexColumn(
                      rs.getInt("ordinal_position"),
                      rs.getString("column_definition"),
                      rs.getBoolean("descending"),
                      rs.getBoolean("included")));

      assertThat(columns)
          .containsExactly(
              new IndexColumn(1, "source", false, false),
              new IndexColumn(2, "object_id", false, false),
              new IndexColumn(3, "COALESCE(start_at, created_at)", true, false),
              new IndexColumn(4, "created_at", true, false),
              new IndexColumn(5, "id", true, false),
              new IndexColumn(6, "stale_at", false, true),
              new IndexColumn(7, "start_at", false, true));
    }
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static void migrateToVersion(DataSource dataSource, String targetVersion) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(targetVersion)
        .load()
        .migrate();
  }

  private record IndexColumn(
      int ordinalPosition, String columnDefinition, boolean descending, boolean included) {}
}
