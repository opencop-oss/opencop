// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V29LayerMergeOperationMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void v29Migration_createsLayerMergeOperationPhaseEnum() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      List<String> enumValues =
          jdbc.queryForList(
              """
              SELECT e.enumlabel
              FROM pg_type t
              JOIN pg_namespace n ON t.typnamespace = n.oid
              JOIN pg_enum e ON e.enumtypid = t.oid
              WHERE n.nspname = 'processor' AND t.typname = 'layer_merge_operation_phase'
              ORDER BY e.enumsortorder
              """,
              String.class);

      assertThat(enumValues)
          .containsExactly("PENDING", "MATERIALIZED", "HYDRATED", "FINALIZED", "FAILED");
    }
  }

  @Test
  void v29Migration_persistsOperationIdentityWithOrderedSources() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
      var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");
      var firstSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655448003");
      var secondSourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655448004");

      jdbc.update(
          """
          INSERT INTO processor.layer_merge_operation (
            target_layer_id,
            job_id,
            source_layer_ids,
            phase
          )
          VALUES (?, ?, ARRAY[?, ?]::uuid[], 'PENDING'::processor.layer_merge_operation_phase)
          """,
          targetLayerId,
          jobId,
          firstSourceId,
          secondSourceId);

      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT source_layer_ids::text
                  FROM processor.layer_merge_operation
                  WHERE target_layer_id = ?
                  """,
                  String.class,
                  targetLayerId))
          .isEqualTo("{%s,%s}".formatted(firstSourceId, secondSourceId));
      assertThat(
              jdbc.queryForObject(
                  """
                  SELECT phase::text
                  FROM processor.layer_merge_operation
                  WHERE job_id = ?
                  """,
                  String.class,
                  jobId))
          .isEqualTo("PENDING");
    }
  }

  @Test
  void v29Migration_rejectsDuplicateJobIds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655448005");
      insertOperation(
          jdbc,
          UUID.fromString("550e8400-e29b-41d4-a716-446655448006"),
          jobId,
          UUID.fromString("550e8400-e29b-41d4-a716-446655448007"),
          UUID.fromString("550e8400-e29b-41d4-a716-446655448008"));

      assertThatThrownBy(
              () ->
                  insertOperation(
                      jdbc,
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448009"),
                      jobId,
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448010"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448011")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v29Migration_rejectsNullJobId() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO processor.layer_merge_operation (
                        target_layer_id,
                        source_layer_ids,
                        phase
                      )
                      VALUES (?, ARRAY[?, ?]::uuid[], 'PENDING'::processor.layer_merge_operation_phase)
                      """,
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448012"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448013"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448014")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v29Migration_rejectsEmptySourceLayerIds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThatThrownBy(
              () ->
                  jdbc.update(
                      """
                      INSERT INTO processor.layer_merge_operation (
                        target_layer_id,
                        job_id,
                        source_layer_ids,
                        phase
                      )
                      VALUES (?, ?, ARRAY[]::uuid[], 'PENDING'::processor.layer_merge_operation_phase)
                      """,
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448015"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448016")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Test
  void v29Migration_rejectsSingleSourceLayerId() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThatThrownBy(
              () ->
                  insertOperation(
                      jdbc,
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448017"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448018"),
                      UUID.fromString("550e8400-e29b-41d4-a716-446655448019")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private static void insertOperation(
      JdbcTemplate jdbc, UUID targetLayerId, UUID jobId, UUID sourceLayerId) {
    jdbc.update(
        """
        INSERT INTO processor.layer_merge_operation (
          target_layer_id,
          job_id,
          source_layer_ids,
          phase
        )
        VALUES (?, ?, ARRAY[?]::uuid[], 'PENDING'::processor.layer_merge_operation_phase)
        """,
        targetLayerId,
        jobId,
        sourceLayerId);
  }

  private static void insertOperation(
      JdbcTemplate jdbc,
      UUID targetLayerId,
      UUID jobId,
      UUID firstSourceLayerId,
      UUID secondSourceLayerId) {
    jdbc.update(
        """
        INSERT INTO processor.layer_merge_operation (
          target_layer_id,
          job_id,
          source_layer_ids,
          phase
        )
        VALUES (?, ?, ARRAY[?, ?]::uuid[], 'PENDING'::processor.layer_merge_operation_phase)
        """,
        targetLayerId,
        jobId,
        firstSourceLayerId,
        secondSourceLayerId);
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
}
