// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V41FlywayMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static final UUID WATCHBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000b41");
  private static final UUID SOURCE_A = UUID.fromString("00000000-0000-0000-0000-000000005041");
  private static final UUID SOURCE_B = UUID.fromString("00000000-0000-0000-0000-000000005042");

  // Seeded against a pre-fold database: a V41 database cannot represent the pre-fix state, since
  // the tightened CHECK rejects the retired codes this migration exists to clean up. The target
  // must name an existing version (Flyway errors on unresolved targets), so it pins the newest
  // migration below the fold at authoring time; the seed only touches V37-era tables and the old
  // V32 vocabulary, so an unrelated version landing in between changes nothing.
  @Test
  void v41Migration_deletesRetiredReasonRowsOnAllEpochsAndTightensTheCheck() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "39");
      var jdbc = new JdbcTemplate(dataSource);
      seedWatchboxFixture(jdbc);
      // The retired vocabulary as V32 allowed it: a row stranded at a superseded epoch and a row
      // at the current epoch (the shape that breaks @Enumerated(STRING) hydration), beside a
      // live-vocabulary row that must survive.
      insertStatusRow(jdbc, 1, SOURCE_A, "WATCHBOX_RECIPIENT_CAP_EXCEEDED");
      insertStatusRow(jdbc, 2, SOURCE_A, "WATCHBOX_AUDIENCE_UNBOUNDED");
      insertStatusRow(jdbc, 2, SOURCE_B, "WATCHBOX_DISABLED");

      // Stop at the version under test. Later migrations may deliberately transform or retire
      // this fixture; those contracts belong to their own migration tests.
      migrateToVersion(dataSource, "41");

      assertThat(
              jdbc.queryForList(
                  """
                  SELECT config_epoch || ':' || reason_code
                  FROM watchbox.watchbox_source_status
                  ORDER BY config_epoch, source_id
                  """,
                  String.class))
          .containsExactly("2:WATCHBOX_DISABLED");

      // The recreated CHECK rejects the retired codes and keeps excluding read-time-only
      // reasons, while still admitting the live persisted vocabulary.
      assertThatThrownBy(
              () -> insertStatusRow(jdbc, 3, SOURCE_A, "WATCHBOX_RECIPIENT_CAP_EXCEEDED"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertStatusRow(jdbc, 3, SOURCE_A, "WATCHBOX_AUDIENCE_UNBOUNDED"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> insertStatusRow(jdbc, 3, SOURCE_A, "WATCHBOX_DETECTION_NOT_RUNNING"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatCode(() -> insertStatusRow(jdbc, 3, SOURCE_A, "WATCHBOX_OBJECT_ID_CHURN_DETECTED"))
          .doesNotThrowAnyException();

      // The migration is one fold; prove the other two surfaces applied with it.
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM watchbox.watchbox_alert_outbox", Long.class))
          .isZero();
      assertThat(
              jdbc.queryForList(
                  """
                  SELECT column_name FROM information_schema.columns
                  WHERE table_schema = 'watchbox'
                    AND table_name = 'watchbox_detection_heartbeat'
                    AND column_name LIKE 'retained_retry_%'
                  ORDER BY column_name
                  """,
                  String.class))
          .containsExactly(
              "retained_retry_attempts",
              "retained_retry_partitions",
              "retained_retry_reason",
              "retained_retry_since");
    }
  }

  private static void seedWatchboxFixture(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
        UUID.fromString("00000000-0000-0000-0000-00000000d341"),
        "v41-test-delimiter");
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        UUID.fromString("00000000-0000-0000-0000-00000000a241"),
        "v41-test-mapper");
    jdbc.update(
        """
        INSERT INTO profiles.data_profile
          (id, name, delimiter_id, mapper_id, mapper_version)
        VALUES (?, ?, ?, ?, 1)
        """,
        UUID.fromString("00000000-0000-0000-0000-00000000f141"),
        "v41-test-profile",
        UUID.fromString("00000000-0000-0000-0000-00000000d341"),
        UUID.fromString("00000000-0000-0000-0000-00000000a241"));
    for (var sourceId : new UUID[] {SOURCE_A, SOURCE_B}) {
      jdbc.update(
          """
          INSERT INTO profiles.data_source (id, name, data_profile_id, connection_spec)
          VALUES (?, ?, ?, '{}'::jsonb)
          """,
          sourceId,
          "v41-test-source-" + sourceId,
          UUID.fromString("00000000-0000-0000-0000-00000000f141"));
    }
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', ?::jsonb, '{"predicates":[]}'::jsonb)
        """,
        WATCHBOX_ID,
        "v41-test-box",
        "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
    // Advance past the default epoch (the trigger allows increases) so epoch 1 is superseded and
    // epoch 2 is current.
    jdbc.update("UPDATE watchbox.watchbox SET config_epoch = 2 WHERE id = ?", WATCHBOX_ID);
  }

  private static void insertStatusRow(
      JdbcTemplate jdbc, long configEpoch, UUID sourceId, String reasonCode) {
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox_source_status
          (watchbox_id, config_epoch, source_id, status, reason_code)
        VALUES (?, ?, ?, 'DEGRADED', ?)
        """,
        WATCHBOX_ID,
        configEpoch,
        sourceId,
        reasonCode);
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
}
