// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V61ResourceAuditHistoryMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String AUDIT_HISTORY_VERSION = "61";

  private static final String INSERT =
      """
      INSERT INTO audit.audit_history (
          entity_type, entity_id, entity_name, operation, occurred_at,
          actor_user_id, actor_full_name, actor_email, actor_global_admin,
          change, change_version)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
      """;

  @Test
  void recordsARowForEveryAuditedResource() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          insert(jdbc, "DATA_SOURCE", "PATCH", actor, "{}", 1);
          insert(jdbc, "LAYER", "MERGED_FROM", actor, "{}", 1);
          insert(jdbc, "LAYER_GROUP", "USER_GROUPS_CHANGED", actor, "{}", 1);
          insert(jdbc, "MAPPER_PROFILE", "ARCHIVE", actor, "{}", 1);

          assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_history", Long.class))
              .isEqualTo(4L);
        });
  }

  @Test
  void keepsEventTimeActorFactsAsWritten() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);
          insert(jdbc, "LAYER", "PATCH", actor, "{\"changed\":[]}", 1);

          var row =
              jdbc.queryForMap(
                  "SELECT actor_full_name, actor_email, actor_global_admin FROM"
                      + " audit.audit_history");

          assertThat(row)
              .containsEntry("actor_full_name", "Ada Lovelace")
              .containsEntry("actor_email", "ada@example.test")
              .containsEntry("actor_global_admin", true);
        });
  }

  @Test
  void rejectsAnOperationThatBelongsToAnotherResource() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          // MERGED_FROM is in the global vocabulary but is a layer concept.
          assertThatThrownBy(() -> insert(jdbc, "MAPPER_PROFILE", "MERGED_FROM", actor, "{}", 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_mapper_profile_operations");

          // CONNECT belongs to data sources only.
          assertThatThrownBy(() -> insert(jdbc, "LAYER", "CONNECT", actor, "{}", 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_layer_operations");
        });
  }

  @Test
  void allowsUnarchiveOnlyForLayers() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          // The one restore in the product undoes a layer archive whose cache eviction failed.
          insert(jdbc, "LAYER", "UNARCHIVE", actor, "{}", 1);

          for (var entityType : new String[] {"DATA_SOURCE", "LAYER_GROUP", "MAPPER_PROFILE"}) {
            assertThatThrownBy(() -> insert(jdbc, entityType, "UNARCHIVE", actor, "{}", 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("audit_history");
          }
        });
  }

  @Test
  void rejectsUnknownEntityTypesAndOperations() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          assertThatThrownBy(() -> insert(jdbc, "WATCHBOX", "PATCH", actor, "{}", 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_entity_type");

          // An unknown operation violates both the global vocabulary and the layer's own list,
          // and Postgres does not promise which one it reports.
          assertThatThrownBy(() -> insert(jdbc, "LAYER", "FIDDLED_WITH", actor, "{}", 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history");
        });
  }

  @Test
  void rejectsAChangeThatIsNotAnObject() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          assertThatThrownBy(() -> insert(jdbc, "LAYER", "PATCH", actor, "[]", 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_change_is_object");
        });
  }

  @Test
  void rejectsAChangeLargerThanTheCap() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);
          var oversized = "{\"note\":\"" + "x".repeat(70_000) + "\"}";

          assertThatThrownBy(() -> insert(jdbc, "LAYER", "PATCH", actor, oversized, 1))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_change_maximum");
        });
  }

  @Test
  void rejectsANonPositiveChangeVersion() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);

          assertThatThrownBy(() -> insert(jdbc, "LAYER", "PATCH", actor, "{}", 0))
              .isInstanceOf(DataIntegrityViolationException.class)
              .hasMessageContaining("audit_history_change_version_positive");
        });
  }

  @Test
  void rejectsAnActorThatIsNotAKnownUser() throws Exception {
    withMigratedDatabase(
        jdbc ->
            assertThatThrownBy(() -> insert(jdbc, "LAYER", "PATCH", UUID.randomUUID(), "{}", 1))
                .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void keepsHistoryWhenTheResourceItDescribesIsGone() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          var actor = seedUser(jdbc);
          // entity_id is deliberately not a foreign key, so a row survives a hard delete of the
          // resource and cannot be cascaded away with it.
          insert(jdbc, "LAYER_GROUP", "DELETE", actor, "{}", 1);

          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM audit.audit_history WHERE operation = 'DELETE'",
                      Long.class))
              .isEqualTo(1L);
        });
  }

  @Test
  void rejectsUpdatingARow() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          insert(jdbc, "LAYER", "PATCH", seedUser(jdbc), "{}", 1);

          assertThatThrownBy(() -> jdbc.update("UPDATE audit.audit_history SET entity_name = 'x'"))
              .hasMessageContaining("append-only");
        });
  }

  @Test
  void rejectsDeletingARow() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          insert(jdbc, "LAYER", "PATCH", seedUser(jdbc), "{}", 1);

          assertThatThrownBy(() -> jdbc.update("DELETE FROM audit.audit_history"))
              .hasMessageContaining("append-only");
          assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_history", Long.class))
              .isEqualTo(1L);
        });
  }

  @Test
  void allowsAMaintenanceTransactionThatOptsIn() throws Exception {
    withMigratedDatabase(
        jdbc -> {
          insert(jdbc, "LAYER", "PATCH", seedUser(jdbc), "{}", 1);

          jdbc.update(
              "UPDATE audit.audit_history SET actor_full_name = NULL, actor_email = NULL"
                  + " WHERE set_config('audit.maintenance', 'on', true) = 'on'");

          assertThat(
                  jdbc.queryForObject("SELECT actor_email FROM audit.audit_history", String.class))
              .isNull();
        });
  }

  private static void insert(
      JdbcTemplate jdbc,
      String entityType,
      String operation,
      UUID actorUserId,
      String change,
      int changeVersion) {
    jdbc.update(
        INSERT,
        entityType,
        UUID.randomUUID(),
        "a name",
        operation,
        java.sql.Timestamp.from(Instant.now()),
        actorUserId,
        "Ada Lovelace",
        "ada@example.test",
        true,
        change,
        changeVersion);
  }

  private static UUID seedUser(JdbcTemplate jdbc) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, 'audit-actor', 'audit-actor', 'https://idp.example.test', 'audit-actor')
        """,
        id);
    return id;
  }

  private static void withMigratedDatabase(ThrowingConsumer body) throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource);
      body.accept(new JdbcTemplate(dataSource));
    }
  }

  private static void migrate(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(AUDIT_HISTORY_VERSION)
        .load()
        .migrate();
  }

  @FunctionalInterface
  private interface ThrowingConsumer {
    void accept(JdbcTemplate jdbc) throws Exception;
  }
}
