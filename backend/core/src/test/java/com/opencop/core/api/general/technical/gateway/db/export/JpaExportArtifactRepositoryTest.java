// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.business.export.ExportArtifact;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@ComponentWebIT(classes = ExportStorageTestApplication.class)
@TestPropertySource(
    properties = "spring.flyway.locations=filesystem:../sdk/src/main/resources/db/migration")
class JpaExportArtifactRepositoryTest extends AbstractDatabaseIT {

  private static final String EXPORT_TABLE = "export_artifact";
  private static final String EXPORT_SCHEMA = "app";
  private static final String DELETE_EXPORT_ARTIFACTS = "DELETE FROM app.export_artifact";
  private static final Set<String> REQUIRED_COLUMNS =
      Set.of(
          "export_id",
          "subject_type",
          "subject_id",
          "creator_id",
          "format",
          "blob_path",
          "filename",
          "generated_at",
          "expires_at");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JpaExportArtifactRepository repository;

  @BeforeEach
  void setUp() {
    jdbc.execute(DELETE_EXPORT_ARTIFACTS);
  }

  @Test
  void migratedSchemaCreatesExportArtifactMetadataTable() {
    var columns =
        jdbc.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            """,
            String.class,
            EXPORT_SCHEMA,
            EXPORT_TABLE);

    assertTrue(columns.containsAll(REQUIRED_COLUMNS));
  }

  @Test
  void saveAndFindByIdRoundTripEveryMetadataField() {
    var artifact =
        new ExportArtifact(
            UUID.randomUUID(),
            "LAYER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CSV",
            "exports/layer-export.csv",
            "layer-export.csv",
            OffsetDateTime.parse("2026-06-08T13:00:00Z"),
            OffsetDateTime.parse("2026-06-08T14:00:00Z"));

    repository.save(artifact);

    var loaded = repository.findById(artifact.exportId());

    assertTrue(loaded.isPresent());
    assertEquals(artifact, loaded.orElseThrow());
  }

  @Test
  void findByIdReturnsEmptyWhenArtifactDoesNotExist() {
    assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
  }

  @Test
  void findExpiredBeforeReturnsOnlyArtifactsPastTheirExpiryTime() {
    var expired =
        artifact(
            OffsetDateTime.parse("2026-06-08T12:00:00Z"),
            OffsetDateTime.parse("2026-06-08T13:00:00Z"));
    var active =
        artifact(
            OffsetDateTime.parse("2026-06-08T12:00:00Z"),
            OffsetDateTime.parse("2026-06-08T15:00:00Z"));
    repository.save(expired);
    repository.save(active);

    var expiredArtifacts =
        repository.findExpiredBefore(OffsetDateTime.parse("2026-06-08T14:00:00Z"));

    assertEquals(1, expiredArtifacts.size());
    assertEquals(expired.exportId(), expiredArtifacts.get(0).exportId());
    assertEquals(expired.expiresAt(), expiredArtifacts.get(0).expiresAt());
    assertFalse(
        expiredArtifacts.stream()
            .anyMatch(artifact -> active.exportId().equals(artifact.exportId())));
  }

  private static ExportArtifact artifact(OffsetDateTime generatedAt, OffsetDateTime expiresAt) {
    var exportId = UUID.randomUUID();
    return new ExportArtifact(
        exportId,
        "SOURCE",
        UUID.randomUUID(),
        UUID.randomUUID(),
        "CSV",
        "exports/" + exportId + ".csv",
        "source-export.csv",
        generatedAt,
        expiresAt);
  }
}
