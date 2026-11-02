// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FailAfterPersistRecorder;
import com.opencop.core.api.component.support.FailAfterPersistRecorderConfig;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupContainerUseCase;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupMembersUseCase;
import com.opencop.core.api.general.business.usecase.layergroup.LayerGroupUserGroupsUseCase;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest.MemberAdd;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

/**
 * Proves a layer group change and its history row commit or roll back together, against a real
 * database and with the failure induced after the row was already written.
 */
@ComponentWebIT(classes = LayerGroupAuditWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
@Import(FailAfterPersistRecorderConfig.class)
class LayerGroupAuditAtomicityIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("5c0e8400-e29b-41d4-a716-446655446001");
  private static final UUID MEMBER_ID = UUID.fromString("5c0e8400-e29b-41d4-a716-446655446002");
  private static final UUID USER_GROUP_ID = UUID.fromString("5c0e8400-e29b-41d4-a716-446655446003");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private LayerGroupContainerUseCase containerUseCase;
  @Autowired private LayerGroupMembersUseCase membersUseCase;
  @Autowired private LayerGroupUserGroupsUseCase userGroupsUseCase;
  @Autowired private FailAfterPersistRecorder recorder;

  private final OpenCopPrincipal admin =
      new OpenCopPrincipal(
              ADMIN_ID,
              "http://localhost/group-audit-issuer",
              "group-audit-idp-admin",
              "group-audit-subject-admin",
              new OpenCopPrincipal.Profile("group-audit-admin", null, null, null, null, false),
              0,
              Instant.parse("2026-10-14T08:00:00Z"))
          .withRequestCurrentGlobalAdministrator(true);

  @BeforeEach
  void setUp() {
    reset();
    for (var id : new UUID[] {ADMIN_ID, MEMBER_ID}) {
      jdbcTemplate.update(
          """
          INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username)
          VALUES (?, ?, ?, 'https://idp.example.test', ?)
          """,
          id,
          "group-audit-" + id,
          "group-audit-" + id,
          "group-audit-" + id);
    }
    jdbcTemplate.update(
        "INSERT INTO iam.user_group (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING",
        USER_GROUP_ID,
        "group-audit-user-group");
    recorder.resetObservations();
  }

  @AfterEach
  void tearDown() {
    reset();
  }

  @Test
  void create_whenRecordingFailsAfterInsert_rollsBackTheGroupItsOwnerAndTheRow() {
    var name = "atomic-create-" + UUID.randomUUID();
    recorder.failNext(Operation.CREATE);

    assertThatThrownBy(
            () -> containerUseCase.create(new LayerGroupCreateRequest(name, null, null), admin))
        .isInstanceOf(IllegalStateException.class);

    assertThat(recorder.completedDelegateCalls()).isEqualTo(1);
    assertThat(recorder.transactionActiveAtInvocation()).isTrue();
    assertThat(groupsNamed(name)).isZero();
    assertThat(rowsByActor()).isZero();
  }

  @Test
  void patchMembers_whenRecordingFailsAfterInsert_rollsBackTheMembershipAndTheRow() {
    var group = createGroup("atomic-members");
    var rowsBefore = rowsByActor();
    recorder.resetObservations();

    recorder.failNext(Operation.MEMBERS_CHANGED);
    assertThatThrownBy(
            () ->
                membersUseCase.patchMembers(
                    group,
                    new LayerGroupMembersPatchRequest(
                        Set.of(new MemberAdd(MEMBER_ID, LayerGroupRole.MEMBER)), null),
                    admin))
        .isInstanceOf(IllegalStateException.class);

    assertThat(recorder.rowsVisibleBeforeFailure()).isEqualTo(1);
    assertThat(isMember(group, MEMBER_ID)).isFalse();
    assertThat(rowsByActor()).isEqualTo(rowsBefore);
  }

  @Test
  void patchMembers_whenRecordingSucceeds_keepsTheMembershipAndRecordsOnlyWhatChanged() {
    var group = createGroup("atomic-members-happy");

    membersUseCase.patchMembers(
        group,
        new LayerGroupMembersPatchRequest(
            Set.of(
                new MemberAdd(MEMBER_ID, LayerGroupRole.MEMBER),
                new MemberAdd(ADMIN_ID, LayerGroupRole.OWNER)),
            null),
        admin);

    assertThat(isMember(group, MEMBER_ID)).isTrue();
    // The admin already owned the group, so only the new member is in the row.
    var change =
        jdbcTemplate.queryForObject(
            "SELECT change::text FROM audit.audit_history WHERE entity_id = ? AND operation ="
                + " 'MEMBERS_CHANGED'",
            String.class,
            group);
    assertThat(change).contains(MEMBER_ID.toString()).doesNotContain(ADMIN_ID.toString());
  }

  @Test
  void patchMembers_whenNothingChanged_recordsNoRow() {
    var group = createGroup("atomic-members-noop");
    var rowsBefore = rowsByActor();

    membersUseCase.patchMembers(
        group,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(ADMIN_ID, LayerGroupRole.OWNER)), null),
        admin);

    assertThat(rowsByActor()).isEqualTo(rowsBefore);
  }

  @Test
  void delete_whenRecordingFailsAfterInsert_keepsTheGroup() {
    var group = createGroup("atomic-delete");
    recorder.resetObservations();

    recorder.failNext(Operation.DELETE);
    assertThatThrownBy(() -> containerUseCase.delete(group, admin))
        .isInstanceOf(IllegalStateException.class);

    assertThat(groupsNamed("atomic-delete")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_history WHERE entity_id = ? AND operation ="
                    + " 'DELETE'",
                Integer.class,
                group))
        .isZero();
  }

  /**
   * {@code patchUserGroups} declares a checked {@link PersistenceException}, and Spring commits on
   * a checked exception unless told otherwise. This fails if its {@code rollbackFor} is removed:
   * the assignment the service had already applied would be committed while the caller saw an
   * error.
   */
  @Test
  void patchUserGroups_whenACheckedExceptionEscapes_rollsBackTheAssignmentAndTheRow() {
    var group = createGroup("atomic-user-groups");
    var rowsBefore = rowsByActor();
    recorder.resetObservations();

    recorder.failNextWithCheckedException(Operation.USER_GROUPS_CHANGED);
    assertThatThrownBy(
            () ->
                userGroupsUseCase.patchUserGroups(
                    group,
                    new LayerGroupUserGroupsPatchRequest(
                        List.of(new UserGroupAdd(USER_GROUP_ID, LayerGroupRole.MEMBER)), List.of()),
                    admin))
        .isInstanceOf(PersistenceException.class);

    // The row reached the database before the failure, so only a rollback can remove it again.
    assertThat(recorder.rowsVisibleBeforeFailure()).isEqualTo(1);
    assertThat(isUserGroupAssigned(group)).isFalse();
    assertThat(rowsByActor()).isEqualTo(rowsBefore);
  }

  private boolean isUserGroupAssigned(UUID group) {
    return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_group_user_group WHERE layer_group_id = ?",
            Integer.class,
            group)
        > 0;
  }

  private UUID createGroup(String name) {
    return containerUseCase.create(new LayerGroupCreateRequest(name, null, null), admin).id();
  }

  private int groupsNamed(String name) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.layer_group WHERE name = ?", Integer.class, name);
  }

  private boolean isMember(UUID group, UUID user) {
    return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_group_member WHERE layer_group_id = ? AND user_id = ?",
            Integer.class,
            group,
            user)
        > 0;
  }

  private int rowsByActor() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit.audit_history WHERE actor_user_id = ?",
        Integer.class,
        ADMIN_ID);
  }

  private void reset() {
    jdbcTemplate.update(
        "DELETE FROM audit.audit_history WHERE actor_user_id = ?"
            + " AND set_config('audit.maintenance', 'on', true) = 'on'",
        ADMIN_ID);
    jdbcTemplate.update("DELETE FROM iam.layer_group WHERE name LIKE 'atomic-%'");
    jdbcTemplate.update("DELETE FROM opencop_user WHERE id IN (?, ?)", ADMIN_ID, MEMBER_ID);
    jdbcTemplate.update("DELETE FROM iam.user_group WHERE id = ?", USER_GROUP_ID);
  }
}
