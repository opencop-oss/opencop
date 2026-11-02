// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.member;

import static com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes.ELEMENT_NOT_FOUND;

import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberIdEntity;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class LayerMembersService implements LayerMemberRepository {

  private final RepositoryMapper<LayerMemberEntity, LayerMember> mapper;
  private final IamLayerMembersRepository repository;
  private final IamLayerRepository layerRepository;

  public LayerMembersService(
      final IamLayerMembersRepository repository, final IamLayerRepository layerRepository) {
    this.mapper = new LayerMemberMapper();
    this.repository = repository;
    this.layerRepository = layerRepository;
  }

  @Override
  public LayerMember getMember(UUID layerId, UUID userId) throws PersistenceException {
    var member =
        repository
            .findByLayerIdAndUserId(layerId, userId)
            .orElseThrow(() -> new PersistenceException("User not found"));
    return mapper.toModel(member);
  }

  @Override
  public Optional<LayerMember> findMember(UUID layerId, UUID userId) {
    return repository.findByLayerIdAndUserId(layerId, userId).map(mapper::toSanitizedModel);
  }

  @Override
  @Transactional
  public List<LayerMember> getLayerMembers(UUID layerId) {
    var users =
        repository.findPageByLayerId(
            layerId
            //                PageRequest.of(page, size, Sort.by("joinedAt").descending())
            );
    return users.stream().map(mapper::toSanitizedModel).toList();
  }

  @Override
  @Transactional
  public List<LayerMember> getLayersByUserId(UUID userId) {
    List<LayerMemberEntity> entities = repository.findAllLayersByUserId(userId);
    return entities.stream().map(mapper::toSanitizedModel).toList();
  }

  @Override
  @Transactional
  public void addLayerMemberRoles(UUID layerId, Set<UUID> userIds, LayerRole role)
      throws RepositoryException {
    LayerEntity layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new RepositoryException(ELEMENT_NOT_FOUND));
    for (UUID userId : userIds) {
      LayerMemberIdEntity id = new LayerMemberIdEntity(layerId, userId);
      LayerMemberEntity existing = repository.findById(id).orElse(null);
      if (existing == null) {
        LayerMemberEntity tm = new LayerMemberEntity(layer, userId, role);
        repository.save(tm);
      } else {
        this.revokeLayerMemberRoles(
            layerId, userId, List.of(LayerRole.MEMBER, LayerRole.ADMIN, LayerRole.OWNER));
        existing.setRole(role);
        repository.save(existing);
      }
    }
  }

  @Transactional
  public void revokeLayerMemberRoles(UUID layerId, UUID userId, List<LayerRole> roles)
      throws RepositoryException {
    LayerMemberIdEntity id = new LayerMemberIdEntity(layerId, userId);
    LayerMemberEntity existing =
        repository.findById(id).orElseThrow(() -> new RepositoryException(ELEMENT_NOT_FOUND));
    Set<LayerRole> remain = new HashSet<>(EnumSet.of(existing.getRole()));
    roles.forEach(remain::remove);
    existing.setRole(highest(remain));
    repository.save(existing);
  }

  /**
   * Grants each role, raising rather than lowering an existing member.
   *
   * @return each affected id mapped to the role it actually ended with, which is not always the
   *     role asked for: an existing member keeps the higher of the two.
   */
  @Override
  @Transactional
  public Map<UUID, LayerRole> addLayerMembers(UUID layerId, Map<UUID, LayerRole> principalsAndRoles)
      throws RepositoryException {
    LayerEntity layer =
        layerRepository
            .findById(layerId)
            .orElseThrow(() -> new RepositoryException(ELEMENT_NOT_FOUND));
    List<LayerMemberEntity> result = new ArrayList<>();
    var applied = new LinkedHashMap<UUID, LayerRole>();
    for (var e : principalsAndRoles.entrySet()) {
      UUID userId = e.getKey();
      LayerRole role = e.getValue() == null ? LayerRole.MEMBER : e.getValue();
      LayerMemberIdEntity id = new LayerMemberIdEntity(layerId, userId);
      LayerMemberEntity tm = repository.findById(id).orElse(null);
      if (tm == null) {
        tm = new LayerMemberEntity();
        tm.setId(id);
        tm.setLayer(layer);
        tm.setRole(role);
      } else {
        tm.setRole(highest(EnumSet.of(tm.getRole(), role)));
      }
      var saved = repository.save(tm);
      result.add(saved);
      // Read from the entity rather than save's return: the role was decided above, and this
      // must not depend on the repository handing an instance back.
      applied.put(userId, tm.getRole());
    }
    return applied;
  }

  @Override
  @Transactional
  public void removeLayerMembers(UUID layerId, Set<UUID> userIds) {
    repository.deleteAllByLayerIdAndUserIds(layerId, userIds);
  }

  @Override
  public Set<UUID> getOwnerUserIds(UUID layerId) {
    return repository.findUserIdsByLayerIdAndRole(layerId, LayerRole.OWNER);
  }

  private static LayerRole highest(Collection<LayerRole> roles) {
    if (roles == null || roles.isEmpty()) {
      return LayerRole.MEMBER;
    }
    if (roles.contains(LayerRole.OWNER)) {
      return LayerRole.OWNER;
    }
    if (roles.contains(LayerRole.ADMIN)) {
      return LayerRole.ADMIN;
    }
    return LayerRole.MEMBER;
  }
}
