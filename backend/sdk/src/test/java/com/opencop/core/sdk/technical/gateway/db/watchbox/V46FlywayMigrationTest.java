// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.watchbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V46FlywayMigrationTest {
  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final UUID WATCHBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000b46");
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000005046");

  @Test
  void v46Migration_addsNullableDetectionHandles_boundsTheDisplayName_andKeepsLegacyRows()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "45");
      var jdbc = new JdbcTemplate(dataSource);
      seedWatchboxFixture(jdbc);
      jdbc.update(
          """
          INSERT INTO watchbox.watchbox_alert_outbox
            (watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time)
          VALUES (?, 1, ?, 'legacy-object', ?, 'ENTER', now())
          """,
          WATCHBOX_ID,
          SOURCE_ID,
          UUID.fromString("00000000-0000-0000-0000-00000000ee46"));
      jdbc.update(
          """
          INSERT INTO watchbox.watchbox_detection
            (id, watchbox_id, config_epoch, source_id, object_id, event_id, transition, event_time,
             object_display_name)
          VALUES (?, ?, 1, ?, 'legacy-object', ?, 'ENTER', now(), ?)
          """,
          UUID.fromString("00000000-0000-0000-0000-00000000de46"),
          WATCHBOX_ID,
          SOURCE_ID,
          UUID.fromString("00000000-0000-0000-0000-00000000ee46"),
          "n".repeat(300));

      migrateToVersion(dataSource, "46");

      assertThat(
              jdbc.queryForList(
                  """
          SELECT column_name || ':' || data_type || ':' || is_nullable
          FROM information_schema.columns
          WHERE table_schema = 'watchbox' AND table_name = 'watchbox_alert_outbox'
            AND column_name IN ('detection_id', 'object_display_name')
          ORDER BY column_name
          """,
                  String.class))
          .containsExactly("detection_id:uuid:YES", "object_display_name:character varying:YES");
      assertThat(
              jdbc.queryForList(
                  """
          SELECT table_name || ':' || character_maximum_length
          FROM information_schema.columns
          WHERE table_schema = 'watchbox' AND column_name = 'object_display_name'
          ORDER BY table_name
          """,
                  String.class))
          .containsExactly("watchbox_alert_outbox:255", "watchbox_detection:255");
      assertThat(
              jdbc.queryForObject(
                  "SELECT length(object_display_name) FROM watchbox.watchbox_detection",
                  Integer.class))
          .isEqualTo(255);
      var rows =
          jdbc.queryForList(
              """
          SELECT object_id, detection_id, object_display_name
          FROM watchbox.watchbox_alert_outbox
          """);
      assertThat(rows)
          .singleElement()
          .satisfies(
              row -> {
                assertThat(row.get("object_id")).isEqualTo("legacy-object");
                assertThat(row.get("detection_id")).isNull();
                assertThat(row.get("object_display_name")).isNull();
              });
    }
  }

  private static void seedWatchboxFixture(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
        UUID.fromString("00000000-0000-0000-0000-00000000d346"),
        "v46-test-delimiter");
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper (id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        UUID.fromString("00000000-0000-0000-0000-00000000a246"),
        "v46-test-mapper");
    jdbc.update(
        """
        INSERT INTO profiles.data_profile
          (id, name, delimiter_id, mapper_id, mapper_version)
        VALUES (?, ?, ?, ?, 1)
        """,
        UUID.fromString("00000000-0000-0000-0000-00000000f146"),
        "v46-test-profile",
        UUID.fromString("00000000-0000-0000-0000-00000000d346"),
        UUID.fromString("00000000-0000-0000-0000-00000000a246"));
    for (var sourceId : new UUID[] {SOURCE_ID}) {
      jdbc.update(
          """
          INSERT INTO profiles.data_source (id, name, data_profile_id, connection_spec)
          VALUES (?, ?, ?, '{}'::jsonb)
          """,
          sourceId,
          "v46-test-source-" + sourceId,
          UUID.fromString("00000000-0000-0000-0000-00000000f146"));
    }
    jdbc.update(
        """
        INSERT INTO watchbox.watchbox
          (id, name, direction, polygon_json, filter_json)
        VALUES (?, ?, 'BOTH', ?::jsonb, '{"predicates":[]}'::jsonb)
        """,
        WATCHBOX_ID,
        "v46-test-box",
        "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
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
