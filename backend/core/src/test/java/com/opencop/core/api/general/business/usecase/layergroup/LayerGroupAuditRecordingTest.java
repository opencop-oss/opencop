// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.layergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupLayerService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupUserGroupService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.layergroup.AppLayerGroupUserGroupService;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayerResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMembersPatchRequest.MemberAdd;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupUserGroupsPatchRequest.UserGroupAdd;
import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupUserGroupEntity;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pins that layer-group history is computed from the state read before and after each write, so a
 * row says what the group became and not what the request asked for.
 */
class LayerGroupAuditRecordingTest {

  private static final UUID GROUP = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");
  private static final UUID USER_A = UUID.fromString("550e8400-e29b-41d4-a716-446655449002");
  private static final UUID USER_B = UUID.fromString("550e8400-e29b-41d4-a716-446655449003");
  private static final UUID USER_GROUP = UUID.fromString("550e8400-e29b-41d4-a716-446655449004");
  private static final UUID LAYER = UUID.fromString("550e8400-e29b-41d4-a716-446655449005");

  private final LayerGroupAuthorization auth = mock(LayerGroupAuthorization.class);
  private final LayerGroupService groupService = mock(LayerGroupService.class);
  private final AuditRecorder recorder = mock(AuditRecorder.class);
  private final LayerGroupAuditProjection projection = new LayerGroupAuditProjection();
  private final OpenCopPrincipal admin = mock(OpenCopPrincipal.class);

  // A caller always has an id; the Caller type rejects one without. Stubbed here rather than left
  // null so these tests exercise the same construction production does.
  {
    when(admin.internalUserId()).thenReturn(UUID.randomUUID());
  }

  @BeforeEach
  void setUp() throws Exception {
    when(admin.isGlobalAdmin()).thenReturn(true);
    when(auth.getEffectiveContainerRole(any(), any()))
        .thenReturn(Optional.of(LayerGroupRole.OWNER));
    when(groupService.getGroup(GROUP))
        .thenReturn(
            new LayerGroupResponse(
                GROUP, "Ops", null, LayerGroupAccessScope.PRIVATE, Instant.EPOCH));
  }

  @Test
  void patchMembers_recordsWhatChangedNotWhatWasRequested() throws Exception {
    var useCase = new LayerGroupMembersUseCase(auth, groupService, recorder, projection);
    when(groupService.getMembers(GROUP))
        .thenReturn(
            List.of(member(USER_A, LayerGroupRole.OWNER)),
            List.of(member(USER_A, LayerGroupRole.OWNER), member(USER_B, LayerGroupRole.MEMBER)));

    // USER_A is both added and removed: the service lets the removal win, so USER_A stays out of
    // the added list even though the request named them.
    useCase.patchMembers(
        GROUP,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_B, LayerGroupRole.MEMBER)), null),
        admin);

    var event = recordedEvent();
    assertThat(event.entityType()).isEqualTo(EntityType.LAYER_GROUP);
    assertThat(event.operation()).isEqualTo(Operation.MEMBERS_CHANGED);
    assertThat(event.change().delta().added()).containsOnlyKeys(USER_B);
    assertThat(event.change().delta().removed()).isEmpty();
  }

  @Test
  void patchMembers_whenNothingMoved_recordsNoRow() throws Exception {
    var useCase = new LayerGroupMembersUseCase(auth, groupService, recorder, projection);
    var unchanged = List.of(member(USER_A, LayerGroupRole.OWNER));
    when(groupService.getMembers(GROUP)).thenReturn(unchanged, unchanged);

    useCase.patchMembers(
        GROUP,
        new LayerGroupMembersPatchRequest(
            Set.of(new MemberAdd(USER_A, LayerGroupRole.OWNER)), null),
        admin);

    verify(recorder, never()).record(any());
  }

  @Test
  void patchMembers_locksTheGroupBeforeReadingItsState() throws Exception {
    var useCase = new LayerGroupMembersUseCase(auth, groupService, recorder, projection);
    when(groupService.getMembers(GROUP)).thenReturn(List.of(), List.of());

    useCase.patchMembers(GROUP, new LayerGroupMembersPatchRequest(null, null), admin);

    var order = inOrder(groupService);
    order.verify(groupService).lockGroup(GROUP);
    order.verify(groupService).getMembers(GROUP);
    order.verify(groupService).patchMembers(eq(GROUP), any(), any());
  }

  @Test
  void patchUserGroups_recordsRoleChangeAsRoleChangeNotAsAddition() throws Exception {
    var service = mock(AppLayerGroupUserGroupService.class);
    var useCase =
        new LayerGroupUserGroupsUseCase(auth, service, groupService, recorder, projection);
    var beforeAssignment = assignment(USER_GROUP, LayerGroupRole.MEMBER);
    var afterAssignment = assignment(USER_GROUP, LayerGroupRole.ADMIN);
    when(service.listUserGroupsForLayerGroup(GROUP))
        .thenReturn(List.of(beforeAssignment), List.of(afterAssignment));

    useCase.patchUserGroups(
        GROUP,
        new LayerGroupUserGroupsPatchRequest(
            List.of(new UserGroupAdd(USER_GROUP, LayerGroupRole.ADMIN)), List.of()),
        admin);

    var event = recordedEvent();
    assertThat(event.operation()).isEqualTo(Operation.USER_GROUPS_CHANGED);
    assertThat(event.change().delta().roleChanged()).containsOnlyKeys(USER_GROUP);
    assertThat(event.change().delta().added()).isEmpty();
  }

  @Test
  void assignLayers_recordsOnlyLayersThatWereNotAlreadyInTheGroup() throws Exception {
    var layerService = mock(AppLayerGroupLayerService.class);
    var useCase =
        new LayerGroupLayersUseCase(
            auth,
            layerService,
            mock(EffectiveLayerAccessService.class),
            groupService,
            mock(LayersRepository.class),
            recorder,
            projection);
    when(layerService.getLayersForLayerGroup(GROUP))
        .thenReturn(List.of(layer(LayerRole.MEMBER)), List.of(layer(LayerRole.MEMBER)));

    useCase.assignLayers(
        GROUP, new LayerGroupLayersAssignRequest(Set.of(LAYER), LayerRole.MEMBER), admin);

    verify(recorder, never()).record(any());
  }

  @Test
  void removeLayer_recordsTheRemovalFromTheStateAfterTheWrite() throws Exception {
    var layerService = mock(AppLayerGroupLayerService.class);
    var useCase =
        new LayerGroupLayersUseCase(
            auth,
            layerService,
            mock(EffectiveLayerAccessService.class),
            groupService,
            mock(LayersRepository.class),
            recorder,
            projection);
    when(layerService.getLayersForLayerGroup(GROUP))
        .thenReturn(List.of(layer(LayerRole.MEMBER)), List.of());

    useCase.removeLayer(GROUP, LAYER, admin);

    var event = recordedEvent();
    assertThat(event.operation()).isEqualTo(Operation.LAYERS_CHANGED);
    assertThat(event.change().delta().removed()).containsExactly(LAYER);
  }

  @Test
  void patchGroup_locksBeforeReadingTheBeforeState() throws Exception {
    var useCase =
        new LayerGroupContainerUseCase(
            auth,
            groupService,
            mock(LayerGroupLayerService.class),
            mock(LayerGroupUserGroupService.class),
            recorder,
            projection);
    when(groupService.patchGroup(eq(GROUP), any()))
        .thenReturn(
            new LayerGroupResponse(
                GROUP, "Renamed", null, LayerGroupAccessScope.PRIVATE, Instant.EPOCH));

    useCase.patch(GROUP, new LayerGroupPatchRequest("Renamed", null, null), admin);

    var order = inOrder(groupService);
    order.verify(groupService).lockGroup(GROUP);
    order.verify(groupService).getGroup(GROUP);
    order.verify(groupService).patchGroup(eq(GROUP), any());
    assertThat(recordedEvent().operation()).isEqualTo(Operation.PATCH);
  }

  @Test
  void deleteGroup_locksThenRecordsWhatTheCascadeTook() throws Exception {
    var layerService = mock(LayerGroupLayerService.class);
    var useCase =
        new LayerGroupContainerUseCase(
            auth,
            groupService,
            layerService,
            mock(LayerGroupUserGroupService.class),
            recorder,
            projection);
    when(groupService.getMembers(GROUP)).thenReturn(List.of(member(USER_A, LayerGroupRole.OWNER)));

    useCase.delete(GROUP, admin);

    var order = inOrder(groupService);
    order.verify(groupService).lockGroup(GROUP);
    order.verify(groupService).deleteGroup(GROUP);
    var event = recordedEvent();
    assertThat(event.operation()).isEqualTo(Operation.DELETE);
    assertThat(event.change().details()).containsEntry("removedMemberCount", 1);
  }

  private AuditEvent recordedEvent() {
    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    return captor.getValue();
  }

  private static LayerGroupMemberResponse member(UUID userId, LayerGroupRole role) {
    return new LayerGroupMemberResponse(
        userId, "u", "f", "l", "e@example.test", role, Instant.EPOCH);
  }

  private static LayerGroupUserGroupEntity assignment(UUID userGroupId, LayerGroupRole role) {
    var entity = mock(LayerGroupUserGroupEntity.class);
    when(entity.getUserGroupId()).thenReturn(userGroupId);
    when(entity.getRole()).thenReturn(role);
    return entity;
  }

  private static LayerGroupLayerResponse layer(LayerRole role) {
    return new LayerGroupLayerResponse(GROUP, "Ops", LAYER, "Layer", role, Instant.EPOCH);
  }
}
