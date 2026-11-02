// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupLayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupLayerMemberRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.IamLayerGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupLayerEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectiveLayerAccessServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443001");
  private static final UUID GLOBAL_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443002");
  private static final UUID PRIVATE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443003");
  private static final UUID EXPLICIT_GLOBAL_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443004");
  private static final UUID MISSING_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443005");
  private static final UUID GROUP_A_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443006");
  private static final UUID GROUP_B_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443007");
  private static final UUID GROUP_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443008");
  private static final UUID LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443009");
  private static final UUID LG_LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443010");
  private static final UUID USER_GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443011");

  private static final UUID GLOBAL_LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443020");
  private static final UUID GLOBAL_LG_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443021");
  private static final UUID PRIVATE_LG_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443022");
  private static final UUID PRIVATE_LG_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443023");

  private LayersRepository layersRepository;
  private LayerMemberRepository layerMemberRepository;
  private IamGroupMembersRepository groupMembersRepository;
  private IamGroupLayerMemberRepository groupLayerRepository;
  private IamLayerGroupMembersRepository layerGroupMembersRepository;
  private IamLayerGroupLayerRepository layerGroupLayerRepository;
  private IamLayerGroupUserGroupRepository layerGroupUserGroupRepository;
  private IamLayerGroupRepository layerGroupRepository;
  private EffectiveLayerAccessService service;

  @BeforeEach
  void setUp() {
    layersRepository = mock(LayersRepository.class);
    layerMemberRepository = mock(LayerMemberRepository.class);
    groupMembersRepository = mock(IamGroupMembersRepository.class);
    groupLayerRepository = mock(IamGroupLayerMemberRepository.class);
    layerGroupMembersRepository = mock(IamLayerGroupMembersRepository.class);
    layerGroupLayerRepository = mock(IamLayerGroupLayerRepository.class);
    layerGroupUserGroupRepository = mock(IamLayerGroupUserGroupRepository.class);
    layerGroupRepository = mock(IamLayerGroupRepository.class);
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    service =
        new EffectiveLayerAccessService(
            layersRepository,
            layerMemberRepository,
            groupMembersRepository,
            groupLayerRepository,
            layerGroupMembersRepository,
            layerGroupLayerRepository,
            layerGroupUserGroupRepository,
            layerGroupRepository);
  }

  @Test
  void getEffectiveLayers_includesGlobalLayersForUnrelatedUsers() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(
            List.of(
                layer(
                    GLOBAL_LAYER_ID,
                    "Shared Intel",
                    LayerAccessScope.GLOBAL,
                    "2024-01-01T00:00:00Z")));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(
        new LayerInfo(
            GLOBAL_LAYER_ID,
            "Shared Intel",
            LayerRole.MEMBER,
            LayerAccessScope.GLOBAL,
            Instant.parse("2024-01-01T00:00:00Z"),
            true),
        result.getFirst());
    verify(layersRepository)
        .list(
            new LayerReadFilter(
                RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null, null, Set.of()));
  }

  @Test
  void getEffectiveLayers_excludesPrivateLayersForUnrelatedUsers() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());

    var result = service.getEffectiveLayers(USER_ID);

    assertTrue(result.isEmpty());
  }

  @Test
  void getEffectiveLayers_explicitRoleOverridesImplicitGlobalMemberRole() {
    when(layerMemberRepository.getLayersByUserId(USER_ID))
        .thenReturn(
            List.of(
                member(EXPLICIT_GLOBAL_LAYER_ID, USER_ID, LayerRole.ADMIN),
                member(PRIVATE_LAYER_ID, USER_ID, LayerRole.OWNER)));
    when(layersRepository.list(
            matchesIdsFilter(Set.of(EXPLICIT_GLOBAL_LAYER_ID, PRIVATE_LAYER_ID))))
        .thenReturn(
            List.of(
                layer(
                    EXPLICIT_GLOBAL_LAYER_ID,
                    "Shared Admin",
                    LayerAccessScope.GLOBAL,
                    "2024-01-02T00:00:00Z"),
                layer(PRIVATE_LAYER_ID, "Private Ops", "2024-01-03T00:00:00Z")));
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(
            List.of(
                layer(
                    EXPLICIT_GLOBAL_LAYER_ID,
                    "Shared Admin",
                    LayerAccessScope.GLOBAL,
                    "2024-01-02T00:00:00Z")));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(2, result.size());
    assertEquals(LayerRole.ADMIN, findByLayerId(result, EXPLICIT_GLOBAL_LAYER_ID).role());
    assertEquals(
        LayerAccessScope.GLOBAL, findByLayerId(result, EXPLICIT_GLOBAL_LAYER_ID).accessScope());
    assertEquals(LayerRole.OWNER, findByLayerId(result, PRIVATE_LAYER_ID).role());
    assertEquals(LayerAccessScope.PRIVATE, findByLayerId(result, PRIVATE_LAYER_ID).accessScope());
  }

  @Test
  void getEffectiveLayers_skipsMembershipsForMissingLayers() {
    when(layerMemberRepository.getLayersByUserId(USER_ID))
        .thenReturn(
            List.of(
                member(MISSING_LAYER_ID, USER_ID, LayerRole.ADMIN),
                member(PRIVATE_LAYER_ID, USER_ID, LayerRole.MEMBER)));
    when(layersRepository.list(argThat(EffectiveLayerAccessServiceTest::matchesMembershipFilter)))
        .thenReturn(List.of(layer(PRIVATE_LAYER_ID, "Private Ops", "2024-01-03T00:00:00Z")));
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(PRIVATE_LAYER_ID, result.getFirst().layerId());
    verify(layersRepository)
        .list(argThat(EffectiveLayerAccessServiceTest::matchesMembershipFilter));
  }

  @Test
  void getEffectiveLayers_sortsByCreatedAt() {
    when(layerMemberRepository.getLayersByUserId(USER_ID))
        .thenReturn(List.of(member(PRIVATE_LAYER_ID, USER_ID, LayerRole.MEMBER)));
    when(layersRepository.list(matchesIdsFilter(Set.of(PRIVATE_LAYER_ID))))
        .thenReturn(List.of(layer(PRIVATE_LAYER_ID, "Private Ops", "2024-01-03T00:00:00Z")));
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(
            List.of(
                layer(
                    GLOBAL_LAYER_ID,
                    "Shared Intel",
                    LayerAccessScope.GLOBAL,
                    "2024-01-01T00:00:00Z")));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(
        List.of(GLOBAL_LAYER_ID, PRIVATE_LAYER_ID),
        result.stream().map(LayerInfo::layerId).toList());
  }

  // ── Batch group query (N+1 fix) ──────────────────────────────────────────

  @Test
  void getEffectiveLayers_includesLayerAccessibleViaGroup() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(GROUP_A_ID));
    when(groupLayerRepository.findAllByGroupIdIn(Set.of(GROUP_A_ID)))
        .thenReturn(List.of(groupLayerAssignment(GROUP_A_ID, GROUP_LAYER_ID, LayerRole.MEMBER)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(GROUP_LAYER_ID, result.getFirst().layerId());
    assertEquals(LayerRole.MEMBER, result.getFirst().role());
  }

  @Test
  void getEffectiveLayers_takesHighestRoleAcrossGroupsInSingleBatchQuery() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID))
        .thenReturn(Set.of(GROUP_A_ID, GROUP_B_ID));
    // Both groups are assigned to the same layer with different roles — batch returns both
    when(groupLayerRepository.findAllByGroupIdIn(Set.of(GROUP_A_ID, GROUP_B_ID)))
        .thenReturn(
            List.of(
                groupLayerAssignment(GROUP_A_ID, GROUP_LAYER_ID, LayerRole.MEMBER),
                groupLayerAssignment(GROUP_B_ID, GROUP_LAYER_ID, LayerRole.ADMIN)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(LayerRole.ADMIN, result.getFirst().role());
  }

  @Test
  void getEffectiveLayers_groupRoleUpgradesDirectMembershipRole() {
    when(layerMemberRepository.getLayersByUserId(USER_ID))
        .thenReturn(List.of(member(GROUP_LAYER_ID, USER_ID, LayerRole.MEMBER)));
    when(layersRepository.list(matchesIdsFilter(Set.of(GROUP_LAYER_ID))))
        .thenReturn(List.of(layer(GROUP_LAYER_ID, "Ops Layer", "2024-01-04T00:00:00Z")));
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(GROUP_A_ID));
    when(groupLayerRepository.findAllByGroupIdIn(Set.of(GROUP_A_ID)))
        .thenReturn(List.of(groupLayerAssignment(GROUP_A_ID, GROUP_LAYER_ID, LayerRole.ADMIN)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(LayerRole.ADMIN, result.getFirst().role());
  }

  @Test
  void getEffectiveLayers_doesNotCallGroupLayerRepoWhenUserHasNoGroups() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());

    service.getEffectiveLayers(USER_ID);

    verify(groupLayerRepository, never()).findAllByGroupIdIn(Set.of());
  }

  // ── getEffectiveRole ─────────────────────────────────────────────────────

  @Test
  void getEffectiveRole_returnsEmptyWhenLayerIsNotAccessible() throws RepositoryException {
    when(layersRepository.get(PRIVATE_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(PRIVATE_LAYER_ID, "Private Ops", "2024-01-03T00:00:00Z"));
    when(layerMemberRepository.findMember(PRIVATE_LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertEquals(Optional.empty(), service.getEffectiveRole(PRIVATE_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_returnsHighestExplicitRoleForGlobalLayer() throws RepositoryException {
    when(layersRepository.get(EXPLICIT_GLOBAL_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(
            layer(
                EXPLICIT_GLOBAL_LAYER_ID,
                "Shared Admin",
                LayerAccessScope.GLOBAL,
                "2024-01-02T00:00:00Z"));
    when(layerMemberRepository.findMember(EXPLICIT_GLOBAL_LAYER_ID, USER_ID))
        .thenReturn(Optional.of(member(EXPLICIT_GLOBAL_LAYER_ID, USER_ID, LayerRole.ADMIN)));

    assertEquals(
        Optional.of(LayerRole.ADMIN), service.getEffectiveRole(EXPLICIT_GLOBAL_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_returnsImplicitMemberForGlobalLayerWithoutExplicitMembership()
      throws RepositoryException {
    when(layersRepository.get(GLOBAL_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(
            layer(
                GLOBAL_LAYER_ID, "Shared Intel", LayerAccessScope.GLOBAL, "2024-01-01T00:00:00Z"));
    when(layerMemberRepository.findMember(GLOBAL_LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertEquals(Optional.of(LayerRole.MEMBER), service.getEffectiveRole(GLOBAL_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_returnsEmptyWhenLayerDoesNotExist() throws RepositoryException {
    when(layersRepository.get(MISSING_LAYER_ID, RecordVisibility.ALL))
        .thenThrow(new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST));

    assertEquals(Optional.empty(), service.getEffectiveRole(MISSING_LAYER_ID, USER_ID));
  }

  // ── layer-group-based access ──────────────────────────────────────────────

  @Test
  void getEffectiveRole_layerGroupPathGrantsAccess() throws RepositoryException {
    when(layersRepository.get(LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(LG_LAYER_ID, "LG Layer", "2024-01-05T00:00:00Z"));
    when(layerMemberRepository.findMember(LG_LAYER_ID, USER_ID)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(Set.of(LG_ID), LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.EDITOR)));

    assertEquals(Optional.of(LayerRole.EDITOR), service.getEffectiveRole(LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_multipleLayerGroupsAssignSameLayer_returnsMax() throws RepositoryException {
    UUID lgIdB = UUID.fromString("550e8400-e29b-41d4-a716-446655443099");
    when(layersRepository.get(LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(LG_LAYER_ID, "LG Layer", "2024-01-05T00:00:00Z"));
    when(layerMemberRepository.findMember(LG_LAYER_ID, USER_ID)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID))
        .thenReturn(Set.of(LG_ID, lgIdB));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(
            Set.of(LG_ID, lgIdB), LG_LAYER_ID))
        .thenReturn(
            List.of(
                lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.MEMBER),
                lgLayerAssignment(lgIdB, LG_LAYER_ID, LayerRole.OWNER)));

    assertEquals(Optional.of(LayerRole.OWNER), service.getEffectiveRole(LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveLayers_multipleLayerGroupsAssignSameLayer_returnsMax() {
    UUID lgIdB = UUID.fromString("550e8400-e29b-41d4-a716-446655443099");
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID))
        .thenReturn(Set.of(LG_ID, lgIdB));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(LG_ID, lgIdB)))
        .thenReturn(
            List.of(
                lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.EDITOR),
                lgLayerAssignment(lgIdB, LG_LAYER_ID, LayerRole.ADMIN)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(LG_LAYER_ID, result.getFirst().layerId());
    assertEquals(LayerRole.ADMIN, result.getFirst().role());
  }

  @Test
  void getEffectiveRole_directOwnerBeatsLayerGroupMember() throws RepositoryException {
    when(layersRepository.get(LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(LG_LAYER_ID, "LG Layer", "2024-01-05T00:00:00Z"));
    when(layerMemberRepository.findMember(LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.of(member(LG_LAYER_ID, USER_ID, LayerRole.OWNER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(Set.of(LG_ID), LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.MEMBER)));

    assertEquals(Optional.of(LayerRole.OWNER), service.getEffectiveRole(LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveLayers_includesLayerAccessibleViaLayerGroup() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(LG_ID)))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.EDITOR)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(LG_LAYER_ID, result.getFirst().layerId());
    assertEquals(LayerRole.EDITOR, result.getFirst().role());
  }

  @Test
  void getEffectiveLayers_excludesArchivedLayerAccessibleViaLayerGroup() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(LG_ID)))
        .thenReturn(List.of(lgLayerAssignmentArchived(LG_ID, LG_LAYER_ID, LayerRole.EDITOR)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(0, result.size());
  }

  @Test
  void getEffectiveLayers_excludesNonActiveMergeStatesFromGroupDerivedActiveAccess() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID))
        .thenReturn(Set.of(GROUP_A_ID, USER_GROUP_ID));
    when(groupLayerRepository.findAllByGroupIdIn(Set.of(GROUP_A_ID, USER_GROUP_ID)))
        .thenReturn(
            List.of(
                groupLayerAssignment(
                    GROUP_A_ID,
                    GROUP_LAYER_ID,
                    LayerRole.MEMBER,
                    LayerLifecycleState.PENDING_MERGE)));
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(LG_ID)))
        .thenReturn(
            List.of(
                lgLayerAssignment(
                    LG_ID,
                    LG_LAYER_ID,
                    LayerRole.EDITOR,
                    LayerLifecycleState.MERGE_SOURCE_LOCKED)));
    when(layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(
            Set.of(GROUP_A_ID, USER_GROUP_ID)))
        .thenReturn(Set.of(PRIVATE_LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(PRIVATE_LG_ID)))
        .thenReturn(
            List.of(
                lgLayerAssignment(
                    PRIVATE_LG_ID,
                    PRIVATE_LG_LAYER_ID,
                    LayerRole.MEMBER,
                    LayerLifecycleState.MERGE_FAILED)));
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalLayerGroup(GLOBAL_LG_ID)));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(GLOBAL_LG_ID)))
        .thenReturn(
            List.of(
                lgLayerAssignment(
                    GLOBAL_LG_ID,
                    GLOBAL_LG_LAYER_ID,
                    LayerRole.ADMIN,
                    LayerLifecycleState.PENDING_MERGE)));

    var result = service.getEffectiveLayers(USER_ID);

    assertTrue(result.isEmpty());
  }

  @Test
  void getEffectiveLayers_allVisibilityIncludesNonActiveMergeStatesFromGroupDerivedAccess() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ALL, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(GROUP_A_ID));
    when(groupLayerRepository.findAllByGroupIdIn(Set.of(GROUP_A_ID)))
        .thenReturn(
            List.of(
                groupLayerAssignment(
                    GROUP_A_ID,
                    GROUP_LAYER_ID,
                    LayerRole.MEMBER,
                    LayerLifecycleState.PENDING_MERGE)));
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of());

    var result = service.getEffectiveLayers(USER_ID, RecordVisibility.ALL);

    assertEquals(1, result.size());
    assertEquals(GROUP_LAYER_ID, result.getFirst().layerId());
  }

  // ── user-group → layer-group → layer path ────────────────────────────────

  @Test
  void getEffectiveRole_userViaUserGroupViaLayerGroup_returnsLayerRole()
      throws RepositoryException {
    when(layersRepository.get(LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(LG_LAYER_ID, "LG Layer", "2024-01-05T00:00:00Z"));
    when(layerMemberRepository.findMember(LG_LAYER_ID, USER_ID)).thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(Set.of(USER_GROUP_ID)))
        .thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(Set.of(LG_ID), LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.MEMBER)));

    assertEquals(Optional.of(LayerRole.MEMBER), service.getEffectiveRole(LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_directOwnerBeatsUserGroupViaLayerGroupMember_returnsOwner()
      throws RepositoryException {
    when(layersRepository.get(LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(LG_LAYER_ID, "LG Layer", "2024-01-05T00:00:00Z"));
    when(layerMemberRepository.findMember(LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.of(member(LG_LAYER_ID, USER_ID, LayerRole.OWNER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(Set.of(USER_GROUP_ID)))
        .thenReturn(Set.of(LG_ID));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(Set.of(LG_ID), LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.MEMBER)));

    assertEquals(Optional.of(LayerRole.OWNER), service.getEffectiveRole(LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveLayers_userGroupViaLayerGroup_layerIncluded() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of(USER_GROUP_ID));
    when(layerGroupUserGroupRepository.findLayerGroupIdsByUserGroupIdIn(Set.of(USER_GROUP_ID)))
        .thenReturn(Set.of(LG_ID));
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(LG_ID)))
        .thenReturn(List.of(lgLayerAssignment(LG_ID, LG_LAYER_ID, LayerRole.MEMBER)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(LG_LAYER_ID, result.getFirst().layerId());
    assertEquals(LayerRole.MEMBER, result.getFirst().role());
  }

  // ── GLOBAL layer group transitive access ─────────────────────────────────

  @Test
  void getEffectiveRole_layerInGlobalGroup_noExplicitGrant_returnsGroupLayerRole()
      throws RepositoryException {
    when(layersRepository.get(GLOBAL_LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(GLOBAL_LG_LAYER_ID, "GlobalGroupLayer", "2024-01-06T00:00:00Z"));
    when(layerMemberRepository.findMember(GLOBAL_LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    var globalGroup = globalLayerGroup(GLOBAL_LG_ID);
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalGroup));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(
            Set.of(GLOBAL_LG_ID), GLOBAL_LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(GLOBAL_LG_ID, GLOBAL_LG_LAYER_ID, LayerRole.EDITOR)));

    assertEquals(
        Optional.of(LayerRole.EDITOR), service.getEffectiveRole(GLOBAL_LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_layerInGlobalGroup_upgradesExistingLowerRole() throws RepositoryException {
    when(layersRepository.get(GLOBAL_LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(GLOBAL_LG_LAYER_ID, "GlobalGroupLayer", "2024-01-06T00:00:00Z"));
    when(layerMemberRepository.findMember(GLOBAL_LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.of(member(GLOBAL_LG_LAYER_ID, USER_ID, LayerRole.MEMBER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalLayerGroup(GLOBAL_LG_ID)));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(
            Set.of(GLOBAL_LG_ID), GLOBAL_LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(GLOBAL_LG_ID, GLOBAL_LG_LAYER_ID, LayerRole.EDITOR)));

    assertEquals(
        Optional.of(LayerRole.EDITOR), service.getEffectiveRole(GLOBAL_LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_existingHigherRoleBeatsGlobalLayerGroupRole() throws RepositoryException {
    when(layersRepository.get(GLOBAL_LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(GLOBAL_LG_LAYER_ID, "GlobalGroupLayer", "2024-01-06T00:00:00Z"));
    when(layerMemberRepository.findMember(GLOBAL_LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.of(member(GLOBAL_LG_LAYER_ID, USER_ID, LayerRole.OWNER)));
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalLayerGroup(GLOBAL_LG_ID)));
    when(layerGroupLayerRepository.findAllByLayerGroupIdsAndLayerId(
            Set.of(GLOBAL_LG_ID), GLOBAL_LG_LAYER_ID))
        .thenReturn(List.of(lgLayerAssignment(GLOBAL_LG_ID, GLOBAL_LG_LAYER_ID, LayerRole.MEMBER)));

    assertEquals(
        Optional.of(LayerRole.OWNER), service.getEffectiveRole(GLOBAL_LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveRole_layerInPrivateGroup_noExplicitGrant_returnsEmpty()
      throws RepositoryException {
    when(layersRepository.get(PRIVATE_LG_LAYER_ID, RecordVisibility.ALL))
        .thenReturn(layer(PRIVATE_LG_LAYER_ID, "PrivateGroupLayer", "2024-01-07T00:00:00Z"));
    when(layerMemberRepository.findMember(PRIVATE_LG_LAYER_ID, USER_ID))
        .thenReturn(Optional.empty());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of());

    assertEquals(Optional.empty(), service.getEffectiveRole(PRIVATE_LG_LAYER_ID, USER_ID));
  }

  @Test
  void getEffectiveLayers_globalLayerGroupAppliesTransitiveAccess() {
    when(layerMemberRepository.getLayersByUserId(USER_ID)).thenReturn(List.of());
    when(layersRepository.list(matchesIdsFilter(Set.of()))).thenReturn(List.of());
    when(layersRepository.list(
            new LayerReadFilter(RecordVisibility.ACTIVE, LayerAccessScope.GLOBAL, null)))
        .thenReturn(List.of());
    when(groupMembersRepository.findGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    when(layerGroupMembersRepository.findLayerGroupIdsByUserId(USER_ID)).thenReturn(Set.of());
    var globalGroup = globalLayerGroup(GLOBAL_LG_ID);
    when(layerGroupRepository.findAllByAccessScope(LayerGroupAccessScope.GLOBAL))
        .thenReturn(List.of(globalGroup));
    when(layerGroupLayerRepository.findAllByLayerGroupIdIn(Set.of(GLOBAL_LG_ID)))
        .thenReturn(List.of(lgLayerAssignment(GLOBAL_LG_ID, GLOBAL_LG_LAYER_ID, LayerRole.EDITOR)));

    var result = service.getEffectiveLayers(USER_ID);

    assertEquals(1, result.size());
    assertEquals(GLOBAL_LG_LAYER_ID, result.getFirst().layerId());
    assertEquals(LayerRole.EDITOR, result.getFirst().role());
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static GroupLayerMemberEntity groupLayerAssignment(
      UUID groupId, UUID layerId, LayerRole role) {
    return groupLayerAssignment(groupId, layerId, role, LayerLifecycleState.ACTIVE);
  }

  private static GroupLayerMemberEntity groupLayerAssignment(
      UUID groupId, UUID layerId, LayerRole role, LayerLifecycleState lifecycleState) {
    var group = new GroupEntity();
    group.setId(groupId);
    var layerEntity = new LayerEntity();
    layerEntity.setId(layerId);
    layerEntity.setName("layer-" + layerId);
    layerEntity.setLifecycleState(lifecycleState);
    return new GroupLayerMemberEntity(group, layerEntity, role);
  }

  private static Layer layer(UUID id, String name, String createdAt) {
    return layer(id, name, LayerAccessScope.PRIVATE, createdAt);
  }

  private static Layer layer(UUID id, String name, LayerAccessScope accessScope, String createdAt) {
    return new Layer.Builder()
        .withId(id)
        .withName(name)
        .withCreatedAt(Instant.parse(createdAt))
        .withAccessScope(accessScope)
        .build();
  }

  private static LayerMember member(UUID layerId, UUID userId, LayerRole role) {
    return new LayerMember.Builder().withLayerId(layerId).withUserId(userId).withRole(role).build();
  }

  private static LayerInfo findByLayerId(List<LayerInfo> layers, UUID layerId) {
    return layers.stream()
        .filter(layer -> layer.layerId().equals(layerId))
        .findFirst()
        .orElseThrow();
  }

  private static boolean matchesMembershipFilter(LayerReadFilter filter) {
    return filter != null
        && filter.recordVisibilityOrDefault() == RecordVisibility.ACTIVE
        && filter.accessScope() == null
        && filter.name() == null
        && filter.id() == null
        && filter.ids().equals(Set.of(MISSING_LAYER_ID, PRIVATE_LAYER_ID));
  }

  private static LayerReadFilter matchesIdsFilter(Set<UUID> ids) {
    return argThat(
        filter ->
            filter != null
                && filter.recordVisibilityOrDefault() == RecordVisibility.ACTIVE
                && filter.accessScope() == null
                && filter.name() == null
                && filter.id() == null
                && filter.ids().equals(ids));
  }

  private static LayerGroupLayerEntity lgLayerAssignment(UUID lgId, UUID layerId, LayerRole role) {
    return lgLayerAssignment(lgId, layerId, role, LayerLifecycleState.ACTIVE);
  }

  private static LayerGroupLayerEntity lgLayerAssignment(
      UUID lgId, UUID layerId, LayerRole role, LayerLifecycleState lifecycleState) {
    var lg = new LayerGroupEntity();
    lg.setId(lgId);
    var layerEntity = new LayerEntity();
    layerEntity.setId(layerId);
    layerEntity.setName("layer-" + layerId);
    layerEntity.setLifecycleState(lifecycleState);
    return new LayerGroupLayerEntity(lg, layerEntity, role);
  }

  private static LayerGroupEntity globalLayerGroup(UUID id) {
    var lg = new LayerGroupEntity();
    lg.setId(id);
    lg.setAccessScope(LayerGroupAccessScope.GLOBAL);
    return lg;
  }

  private static LayerGroupLayerEntity lgLayerAssignmentArchived(
      UUID lgId, UUID layerId, LayerRole role) {
    var lg = new LayerGroupEntity();
    lg.setId(lgId);
    var layerEntity = new LayerEntity();
    layerEntity.setId(layerId);
    layerEntity.setName("layer-" + layerId);
    layerEntity.setArchivedAt(Instant.parse("2024-06-01T00:00:00Z"));
    layerEntity.setLifecycleState(LayerLifecycleState.ARCHIVED);
    return new LayerGroupLayerEntity(lg, layerEntity, role);
  }
}
