// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FailAfterPersistRecorder;
import com.opencop.core.api.component.support.FailAfterPersistRecorderConfig;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.ports.usecase.profile.mapping.DeleteMappingProfile;
import com.opencop.core.api.general.ports.usecase.profile.mapping.SaveMappingProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * Proves the ledger's central promise for mapper profiles: the change and its history row commit or
 * roll back together.
 *
 * <p>The induced failure happens <em>after</em> the audit insert has been flushed and is visible to
 * the transaction, so a passing test means the real rollback undid a row the database had already
 * accepted, not that the insert never ran.
 */
@ComponentWebIT(classes = MapperAuditWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
@Import(FailAfterPersistRecorderConfig.class)
class MapperAuditAtomicityIT extends AbstractWebIT {

  private static final UUID ACTOR_ID = UUID.fromString("5c0e8400-e29b-41d4-a716-446655445001");
  private static final Caller ACTOR =
      new Caller(ACTOR_ID, "Atomicity Admin", "atomicity.admin@example.test", true);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private SaveMappingProfile saveMappingProfile;
  @Autowired private DeleteMappingProfile deleteMappingProfile;
  @Autowired private FailAfterPersistRecorder recorder;

  @BeforeEach
  void setUp() {
    reset();
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
        VALUES (?, 'atomicity-actor', 'atomicity-actor', 'https://idp.example.test',
                'atomicity-actor')
        """,
        ACTOR_ID);
    recorder.resetObservations();
  }

  @AfterEach
  void tearDown() {
    reset();
  }

  @Test
  void create_whenRecordingFailsAfterInsert_rollsBackTheMapperAndTheRow() {
    var id = UUID.randomUUID();
    recorder.failNext(Operation.CREATE);

    assertThatThrownBy(() -> saveMappingProfile.create(meta(id, "atomicity-create", null), ACTOR))
        .isInstanceOf(IllegalStateException.class);

    assertFailureObservedAfterFlush();
    assertThat(mapperVersions(id)).isZero();
    assertThat(auditRows(id)).isZero();
  }

  @Test
  void patch_whenRecordingFailsAfterInsert_rollsBackTheNewVersionAndTheRow() {
    var id = UUID.randomUUID();
    saveMappingProfile.create(meta(id, "atomicity-patch", null), ACTOR);
    recorder.resetObservations();
    var versionsBefore = mapperVersions(id);
    var rowsBefore = auditRows(id);

    recorder.failNext(Operation.PATCH);
    assertThatThrownBy(
            () -> saveMappingProfile.update(meta(id, "atomicity-patch", "a description"), ACTOR))
        .isInstanceOf(IllegalStateException.class);

    assertFailureObservedAfterFlush();
    // The new version is gone with the row that described it, leaving exactly what was there.
    assertThat(mapperVersions(id)).isEqualTo(versionsBefore);
    assertThat(auditRows(id)).isEqualTo(rowsBefore);
  }

  @Test
  void archive_whenRecordingFailsAfterInsert_rollsBackTheArchiveAndTheRow() {
    var id = UUID.randomUUID();
    saveMappingProfile.create(meta(id, "atomicity-archive", null), ACTOR);
    recorder.resetObservations();

    recorder.failNext(Operation.ARCHIVE);
    assertThatThrownBy(() -> deleteMappingProfile.archive(id, ACTOR))
        .isInstanceOf(IllegalStateException.class);

    assertFailureObservedAfterFlush();
    assertThat(archived(id)).isFalse();
    assertThat(auditRows(id)).isEqualTo(1);
  }

  @Test
  void create_whenRecordingSucceeds_keepsBothTheMapperAndTheRow() {
    var id = UUID.randomUUID();

    saveMappingProfile.create(meta(id, "atomicity-happy", null), ACTOR);

    assertThat(mapperVersions(id)).isEqualTo(1);
    assertThat(auditRows(id)).isEqualTo(1);
  }

  /** The delegate ran to completion inside a real transaction, and its row was already visible. */
  private void assertFailureObservedAfterFlush() {
    assertThat(recorder.completedDelegateCalls()).isEqualTo(1);
    assertThat(recorder.transactionActiveAtInvocation()).isTrue();
    assertThat(recorder.rowsVisibleBeforeFailure()).isEqualTo(1);
  }

  private static MapperProfileFullMeta meta(UUID id, String name, String description) {
    return new MapperProfileFullMeta(
        MapperProfile.builder()
            .withId(id)
            .withName(name)
            .withDescription(description)
            .withEditable(true)
            .withArchived(false)
            .build(),
        "sample.xml",
        new byte[] {1, 2, 3});
  }

  private int mapperVersions(UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM profiles.data_mapper WHERE id = ?", Integer.class, id);
  }

  private boolean archived(UUID id) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT bool_or(archived) FROM profiles.data_mapper WHERE id = ?", Boolean.class, id));
  }

  private int auditRows(UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit.audit_history WHERE entity_id = ?", Integer.class, id);
  }

  /**
   * Scoped to this test's own actor: a blanket delete of {@code profiles.data_mapper} would remove
   * the mappers that other tests in the same database still hold connections on.
   */
  private void reset() {
    jdbcTemplate.update(
        "DELETE FROM audit.audit_history WHERE actor_user_id = ?"
            + " AND set_config('audit.maintenance', 'on', true) = 'on'",
        ACTOR_ID);
    jdbcTemplate.update("DELETE FROM profiles.data_mapper WHERE created_by = ?", ACTOR_ID);
    jdbcTemplate.update(
        """
        DELETE FROM profiles.mapper
         WHERE NOT EXISTS (SELECT 1 FROM profiles.data_mapper WHERE data_mapper.id = mapper.id)
        """);
    jdbcTemplate.update("DELETE FROM opencop_user WHERE id = ?", ACTOR_ID);
  }
}
