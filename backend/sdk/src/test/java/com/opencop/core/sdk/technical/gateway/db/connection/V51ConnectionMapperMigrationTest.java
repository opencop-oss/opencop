// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V51ConnectionMapperMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String PRE_MIGRATION_VERSION = "50";
  private static final String CONNECTION_MAPPER_VERSION = "51";
  private static final String ASSIGNED_INSTANCE = "connect-1";

  @Test
  void backfillsEveryConnectionWithItsProfilesMapperWithoutTouchingEditTimestamps()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var profile = seedProfile(jdbc);
      var sources =
          List.of(
              seedSource(jdbc, "TCP", profile.id(), true, false, ASSIGNED_INSTANCE),
              seedSource(jdbc, "TCP", profile.id(), false, true, null),
              seedSource(jdbc, "UDP", profile.id(), false, false, null),
              seedSource(jdbc, "RTSP", profile.id(), true, false, ASSIGNED_INSTANCE),
              seedSource(jdbc, "KAFKA", profile.id(), true, true, null));
      var updatedAtBefore = sources.stream().map(source -> updatedAt(jdbc, source)).toList();

      migrate(dataSource, CONNECTION_MAPPER_VERSION);

      for (var source : sources) {
        assertThat(row(jdbc, source))
            .containsEntry("mapper_id", profile.mapperId())
            .containsEntry("data_profile_id", profile.id());
      }
      assertThat(sources.stream().map(source -> updatedAt(jdbc, source)).toList())
          .isEqualTo(updatedAtBefore);
      assertThat(row(jdbc, sources.get(1)))
          .containsEntry("enabled", false)
          .containsEntry("archived", true);
      assertThat(row(jdbc, sources.getFirst()))
          .containsEntry("enabled", true)
          .containsEntry("assigned_instance", ASSIGNED_INSTANCE);
    }
  }

  @Test
  void everyConnectionMustNameAMapper() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, CONNECTION_MAPPER_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var profileId = seedProfile(jdbc).id();

      assertThatThrownBy(() -> seedSource(jdbc, "UDP", profileId, true, false, null))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void afterTheBackfillAMapperChangeStampsTheConnectionEditTimestamp() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, CONNECTION_MAPPER_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var profile = seedProfile(jdbc);
      var replacement = seedProfile(jdbc);
      var source = seedSourceWithMapper(jdbc, profile);
      var createdUpdatedAt = updatedAt(jdbc, source);

      jdbc.update(
          "UPDATE profiles.data_source SET mapper_id = ? WHERE id = ?",
          replacement.mapperId(),
          source);

      assertThat(updatedAt(jdbc, source)).isAfter(createdUpdatedAt);
    }
  }

  @Test
  void unresolvedMapperAbortsTheMigrationWithoutPartialChanges() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, PRE_MIGRATION_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var profile = seedProfile(jdbc);
      jdbc.update("ALTER TABLE profiles.data_profile DROP CONSTRAINT fk_data_profile_mapper");
      jdbc.update(
          "UPDATE profiles.data_profile SET mapper_id = ? WHERE id = ?",
          UUID.randomUUID(),
          profile.id());
      seedSource(jdbc, "TCP", profile.id(), false, true, null);

      assertThatThrownBy(() -> migrate(dataSource, CONNECTION_MAPPER_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining("unresolved");

      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT count(*)
                  FROM information_schema.columns
                  WHERE table_schema = 'profiles'
                    AND table_name = 'data_source'
                    AND column_name = 'mapper_id'
                  """,
                  Integer.class))
          .isZero();
    }
  }

  private record SeededProfile(UUID id, UUID mapperId) {}

  private static SeededProfile seedProfile(JdbcTemplate jdbc) {
    var delimiterId = UUID.randomUUID();
    var mapperId = UUID.randomUUID();
    var profileId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
        delimiterId,
        "delimiter-" + delimiterId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, 1, ?, '{}'::jsonb)",
        mapperId,
        "mapper-" + mapperId);
    jdbc.update(
        """
        INSERT INTO profiles.data_profile (id, name, delimiter_id, mapper_id, mapper_version)
        VALUES (?, ?, ?, ?, 1)
        """,
        profileId,
        "profile-" + profileId,
        delimiterId,
        mapperId);
    return new SeededProfile(profileId, mapperId);
  }

  private static UUID seedSource(
      JdbcTemplate jdbc,
      String provider,
      UUID profileId,
      boolean enabled,
      boolean archived,
      String assignedInstance) {
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, data_profile_id, connection_spec, enabled, assigned_instance,
           archived_at, archived_by)
        VALUES (?, ?, ?::profiles.transport_kind, ?, '{}'::jsonb, ?, ?,
                CASE WHEN ? THEN now() END, ?)
        """,
        sourceId,
        "source-" + sourceId,
        provider,
        profileId,
        enabled,
        assignedInstance,
        archived,
        archived ? seedUser(jdbc) : null);
    return sourceId;
  }

  private static UUID seedSourceWithMapper(JdbcTemplate jdbc, SeededProfile profile) {
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, data_profile_id, mapper_id, connection_spec)
        VALUES (?, ?, 'TCP', ?, ?, '{}'::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        profile.id(),
        profile.mapperId());
    return sourceId;
  }

  private static UUID seedUser(JdbcTemplate jdbc) {
    var userId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, ?, ?, 'https://issuer.test', ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "user-" + userId);
    return userId;
  }

  private static Map<String, Object> row(JdbcTemplate jdbc, UUID sourceId) {
    return jdbc.queryForMap(
        """
        SELECT data_profile_id, mapper_id, enabled, archived_at IS NOT NULL AS archived,
               assigned_instance
        FROM profiles.data_source
        WHERE id = ?
        """,
        sourceId);
  }

  private static Timestamp updatedAt(JdbcTemplate jdbc, UUID sourceId) {
    return jdbc.queryForObject(
        "SELECT updated_at FROM profiles.data_source WHERE id = ?", Timestamp.class, sourceId);
  }

  private static void migrate(DataSource dataSource, String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(target)
        .load()
        .migrate();
  }
}
