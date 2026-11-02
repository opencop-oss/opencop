// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberIdEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LayerMembersServiceTest {

  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447002");

  private IamLayerMembersRepository repository;
  private IamLayerRepository layerRepository;
  private LayerMembersService service;

  @BeforeEach
  void setUp() {
    repository = org.mockito.Mockito.mock(IamLayerMembersRepository.class);
    layerRepository = org.mockito.Mockito.mock(IamLayerRepository.class);
    service = new LayerMembersService(repository, layerRepository);
  }

  @Test
  void getMember_whenMissing_throwsPersistenceException() {
    when(repository.findByLayerIdAndUserId(LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertThrows(PersistenceException.class, () -> service.getMember(LAYER_ID, USER_ID));
  }

  @Test
  void findMember_whenMissing_returnsEmpty() {
    when(repository.findByLayerIdAndUserId(LAYER_ID, USER_ID)).thenReturn(Optional.empty());

    assertTrue(service.findMember(LAYER_ID, USER_ID).isEmpty());
  }

  @Test
  void findMember_whenPresent_returnsSanitizedMember() {
    var entity = new LayerMemberEntity(layerEntity(), USER_ID, LayerRole.ADMIN);
    when(repository.findByLayerIdAndUserId(LAYER_ID, USER_ID)).thenReturn(Optional.of(entity));

    var result = service.findMember(LAYER_ID, USER_ID);

    assertTrue(result.isPresent());
    assertEquals(USER_ID, result.get().getUserId());
    assertEquals(LAYER_ID, result.get().getLayerId());
    assertEquals(LayerRole.ADMIN, result.get().getRole());
  }

  @Test
  void addLayerMemberRoles_whenLayerMissing_throwsRepositoryException() {
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.empty());

    var exception =
        assertThrows(
            RepositoryException.class,
            () -> service.addLayerMemberRoles(LAYER_ID, Set.of(USER_ID), LayerRole.ADMIN));

    assertEquals(RepositoryExceptionCodes.ELEMENT_NOT_FOUND.code(), exception.getCode());
  }

  @Test
  void addLayerMemberRoles_whenMemberIsNew_createsMember() throws Exception {
    var layer = layerEntity();
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findById(new LayerMemberIdEntity(LAYER_ID, USER_ID)))
        .thenReturn(Optional.empty());
    when(repository.save(any(LayerMemberEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.addLayerMemberRoles(LAYER_ID, Set.of(USER_ID), LayerRole.ADMIN);

    var captor = ArgumentCaptor.forClass(LayerMemberEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(USER_ID, captor.getValue().getUserId());
    assertEquals(LayerRole.ADMIN, captor.getValue().getRole());
  }

  @Test
  void addLayerMemberRoles_whenMemberExists_updatesRole() throws Exception {
    var layer = layerEntity();
    var existing = new LayerMemberEntity(layer, USER_ID, LayerRole.MEMBER);
    var id = new LayerMemberIdEntity(LAYER_ID, USER_ID);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findById(id)).thenReturn(Optional.of(existing), Optional.of(existing));

    service.addLayerMemberRoles(LAYER_ID, Set.of(USER_ID), LayerRole.OWNER);

    assertEquals(LayerRole.OWNER, existing.getRole());
    verify(repository, times(2)).save(existing);
  }

  @Test
  void addLayerMembers_defaultsNullRoleToMember() throws Exception {
    var layer = layerEntity();
    var id = new LayerMemberIdEntity(LAYER_ID, USER_ID);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findById(id)).thenReturn(Optional.empty());
    when(repository.save(any(LayerMemberEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var roles = new HashMap<UUID, LayerRole>();
    roles.put(USER_ID, null);
    service.addLayerMembers(LAYER_ID, roles);

    var captor = ArgumentCaptor.forClass(LayerMemberEntity.class);
    verify(repository).save(captor.capture());
    assertEquals(LayerRole.MEMBER, captor.getValue().getRole());
  }

  @Test
  void addLayerMembers_keepsHighestExistingRole() throws Exception {
    var layer = layerEntity();
    var existing = new LayerMemberEntity(layer, USER_ID, LayerRole.ADMIN);
    var id = new LayerMemberIdEntity(LAYER_ID, USER_ID);
    when(layerRepository.findById(LAYER_ID)).thenReturn(Optional.of(layer));
    when(repository.findById(id)).thenReturn(Optional.of(existing));

    service.addLayerMembers(LAYER_ID, Map.of(USER_ID, LayerRole.MEMBER));

    assertEquals(LayerRole.ADMIN, existing.getRole());
    verify(repository).save(existing);
  }

  @Test
  void removeLayerMembers_delegatesToRepository() {
    service.removeLayerMembers(LAYER_ID, Set.of(USER_ID));

    verify(repository).deleteAllByLayerIdAndUserIds(LAYER_ID, Set.of(USER_ID));
  }

  private static LayerEntity layerEntity() {
    var layer = new LayerEntity();
    layer.setId(LAYER_ID);
    layer.setName("Ops");
    layer.setAccessScope(LayerAccessScope.PRIVATE);
    return layer;
  }
}
