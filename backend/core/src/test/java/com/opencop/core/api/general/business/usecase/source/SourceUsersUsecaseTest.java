// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SourceUsersUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655446501");
  private static final UUID HOLDER = UUID.fromString("550e8400-e29b-41d4-a716-446655446502");
  private static final UUID NEWCOMER = UUID.fromString("550e8400-e29b-41d4-a716-446655446503");

  private ServerManager<Server> serverManager;
  private ConnectionMetadataGateway connections;
  private AuditRecorder recorder;
  private SourceUsersUsecase useCase;
  private final OpenCopPrincipal caller = mock(OpenCopPrincipal.class);

  // A caller always has an id; the Caller type rejects one without. Stubbed here rather than left
  // null so these tests exercise the same construction production does.
  {
    when(caller.internalUserId()).thenReturn(UUID.randomUUID());
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    serverManager = mock(ServerManager.class);
    connections = mock(ConnectionMetadataGateway.class);
    recorder = mock(AuditRecorder.class);
    useCase =
        new SourceUsersUsecase(
            serverManager,
            mock(SourceRelationshipAuthorizationService.class),
            connections,
            recorder,
            new DataSourceAuditProjection());
    when(connections.lockById(SOURCE_ID)).thenReturn(locked());
    permissionsMoveFrom(Set.of(user(HOLDER, ServerRole.VIEWER)));
  }

  /**
   * Drives the mocked access service through the states the write moves between. The row is built
   * from what the permission set became, so a mock answering the same set twice records nothing —
   * which is the point: the service upserts and skips ids it cannot resolve.
   */
  @SafeVarargs
  private void permissionsMoveFrom(Set<SourceUser>... states) {
    var first = states[0];
    var rest = java.util.Arrays.copyOfRange(states, 1, states.length);
    when(serverManager.getUsers(SOURCE_ID)).thenReturn(first, rest);
  }

  /** The locked read the use case takes the row's entity name from. */
  private static ConnectionMetadata locked() {
    return new ConnectionMetadata(
        SOURCE_ID, "Alpha Feed", null, null, null, true, null, true, null);
  }

  private static SourceUser user(UUID id, ServerRole role) {
    var sourceUser = new SourceUser();
    sourceUser.setUserId(id);
    sourceUser.setRole(role);
    return sourceUser;
  }

  @Test
  void updateUsers_locksTheSourceBeforeReadingTheRolesItDiffsAgainst() {
    permissionsMoveFrom(
        Set.of(user(HOLDER, ServerRole.VIEWER)),
        Set.of(user(HOLDER, ServerRole.VIEWER), user(NEWCOMER, ServerRole.ADMIN)));

    useCase.updateUsers(
        SOURCE_ID, new UpdateSourcePermissionsRequest(null, Set.of(NEWCOMER), null), caller);

    var order = inOrder(connections, serverManager);
    order.verify(connections).lockById(SOURCE_ID);
    order.verify(serverManager).getUsers(SOURCE_ID);
    order.verify(serverManager).updateUsers(any(), any());
  }

  @Test
  void updateUsers_recordsAGrantAndARoleChangeSeparately() {
    permissionsMoveFrom(
        Set.of(user(HOLDER, ServerRole.VIEWER)),
        Set.of(user(HOLDER, ServerRole.OWNER), user(NEWCOMER, ServerRole.ADMIN)));

    useCase.updateUsers(
        SOURCE_ID,
        new UpdateSourcePermissionsRequest(null, Set.of(NEWCOMER), Set.of(HOLDER)),
        caller);

    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    assertThat(captor.getValue().operation()).isEqualTo(Operation.MEMBERS_CHANGED);
    assertThat(captor.getValue().change().delta().added()).containsOnlyKeys(NEWCOMER);
    assertThat(captor.getValue().change().delta().roleChanged()).containsOnlyKeys(HOLDER);
    // The name comes from the locked read, so the row names the source rather than only its id.
    assertThat(captor.getValue().entityName()).isEqualTo("Alpha Feed");
  }

  @Test
  void updateUsers_whenTheSameRoleIsSavedAgain_recordsNoRow() {
    permissionsMoveFrom(
        Set.of(user(HOLDER, ServerRole.VIEWER)), Set.of(user(HOLDER, ServerRole.VIEWER)));

    useCase.updateUsers(
        SOURCE_ID, new UpdateSourcePermissionsRequest(Set.of(HOLDER), null, null), caller);

    verify(recorder, never()).record(any());
  }

  @Test
  void deleteUsers_locksTheSourceAndRecordsOnlyHoldersOfAccess() {
    permissionsMoveFrom(Set.of(user(HOLDER, ServerRole.VIEWER)), Set.of());

    useCase.deleteUsers(SOURCE_ID, Set.of(HOLDER, NEWCOMER), caller);

    verify(connections).lockById(SOURCE_ID);
    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    assertThat(captor.getValue().change().delta().removed()).containsExactly(HOLDER);
  }
}
