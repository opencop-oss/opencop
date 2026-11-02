// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.layer;

import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.LayerMemberRepository;
import com.opencop.core.api.iam.ports.gateway.repository.LayersRepository;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerExceptionCodes;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManagerException;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.model.iam.layer.LayerCreateRequest;
import com.opencop.core.sdk.model.iam.layer.LayerPatchRequest;
import com.opencop.core.sdk.model.iam.layer.LayerReadFilter;
import com.opencop.core.sdk.model.iam.layer.UpdateLayerRoleRequest;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class LayerAccessManager<L, LM> implements LayerManager<L, LM> {

  private final Presenter<L, Layer> layerPresenter;
  private final Presenter<LM, LayerMember> layerMemberPresenter;
  private final LayersRepository layersRepository;
  private final LayerMemberRepository layerMemberRepository;
  private final ServerLayerRepository serverLayerRepository;
  private final UserService userService;
  private final EffectiveLayerAccessService effectiveLayerAccessService;

  public LayerAccessManager(
      final Presenter<L, Layer> layerPresenter,
      final Presenter<LM, LayerMember> layerMemberPresenter,
      final LayersRepository layersRepository,
      final LayerMemberRepository layerMemberRepository,
      final UserService userService,
      final ServerLayerRepository serverLayerRepository,
      final EffectiveLayerAccessService effectiveLayerAccessService) {
    this.layerPresenter = layerPresenter;
    this.layerMemberPresenter = layerMemberPresenter;
    this.layersRepository = layersRepository;
    this.userService = userService;
    this.layerMemberRepository = layerMemberRepository;
    this.serverLayerRepository = serverLayerRepository;
    this.effectiveLayerAccessService = effectiveLayerAccessService;
  }

  @Override
  public L create(LayerCreateRequest request, UUID createdBy) {
    try {
      return layerPresenter.present(
          layersRepository.create(request.name(), request.accessScope(), createdBy));
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  /** Maps repository exception codes to appropriate HTTP status codes. */
  private HttpStatus mapRepositoryExceptionStatus(RepositoryException e) {
    var code = e.getCode();
    if (code.equals(RepositoryExceptionCodes.ALREADY_EXIST.code())
        || code.equals(RepositoryExceptionCodes.NAME_ALREADY_TAKEN.code())) {
      return HttpStatus.CONFLICT;
    }
    if (code.equals(RepositoryExceptionCodes.DOES_NOT_EXIST.code())
        || code.equals(RepositoryExceptionCodes.ELEMENT_NOT_FOUND.code())) {
      return HttpStatus.NOT_FOUND;
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  @Override
  public L get(UUID layerId) {
    try {
      var layer = layersRepository.get(layerId);
      return layerPresenter.present(layer);
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  @Override
  public List<L> list() {
    var filter = new LayerReadFilter(RecordVisibility.ACTIVE, null, null, null, null);
    var layers = layersRepository.list(filter);
    return layers.stream().map(layerPresenter::present).toList();
  }

  @Override
  public List<L> list(LayerReadFilter filter) {
    var layers = layersRepository.list(filter);
    return layers.stream().map(layerPresenter::present).toList();
  }

  @Override
  public void update(UUID layerId, LayerPatchRequest request, UUID updatedBy) {
    try {
      layersRepository.update(
          layerId, request.name(), request.accessScope(), request.defaultVisible(), updatedBy);
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  @Override
  public void lock(UUID layerId) {
    try {
      layersRepository.lock(layerId);
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  @Override
  public void archive(UUID layerId, UUID archivedBy) {
    try {
      layersRepository.archive(layerId, archivedBy);
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  @Override
  public void unarchive(UUID layerId) {
    try {
      layersRepository.unarchive(layerId);
    } catch (RepositoryException e) {
      throw new LayerManagerException(e.getCode(), e.getMessage(), mapRepositoryExceptionStatus(e));
    }
  }

  @Override
  public List<LM> listLayerMembers(UUID layerId) {
    var layerMembers = layerMemberRepository.getLayerMembers(layerId);
    layerMembers.forEach(
        layerMember -> {
          try {
            var member = userService.get(layerMember.getUserId());
            layerMember.setUsername(member.username());
            layerMember.setFirstName(member.firstName());
            layerMember.setLastName(member.lastName());
            layerMember.setEmail(member.email());
          } catch (IdpException e) {
            throw new LayerManagerException(
                e.getCode(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
          }
        });
    return layerMembers.stream().map(layerMemberPresenter::present).toList();
  }

  @Override
  public Optional<LayerRole> getMemberRole(UUID layerId, UUID userId) {
    return effectiveLayerAccessService.getEffectiveRole(layerId, userId);
  }

  @Override
  public void updateLayerMemberRole(UUID layerId, UpdateLayerRoleRequest request) {
    try {
      // Check if this would orphan the layer (remove all owners)
      validateOwnerCountAfterRoleUpdate(layerId, request);

      this.layerMemberRepository.addLayerMemberRoles(layerId, request.members(), LayerRole.MEMBER);
      this.layerMemberRepository.addLayerMemberRoles(layerId, request.admins(), LayerRole.ADMIN);
      this.layerMemberRepository.addLayerMemberRoles(layerId, request.editors(), LayerRole.EDITOR);
      this.layerMemberRepository.addLayerMemberRoles(layerId, request.owners(), LayerRole.OWNER);
    } catch (RepositoryException e) {
      throw new LayerManagerException(
          e.getCode(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Validates that updating roles won't leave the layer without any owners. Simulates the change by
   * building the projected set of owners after the update.
   */
  private void validateOwnerCountAfterRoleUpdate(UUID layerId, UpdateLayerRoleRequest request) {
    var projectedOwners = new HashSet<>(layerMemberRepository.getOwnerUserIds(layerId));

    // Remove owners being demoted to members or admins
    if (request.members() != null) projectedOwners.removeAll(request.members());
    if (request.admins() != null) projectedOwners.removeAll(request.admins());

    // Add new owners
    if (request.owners() != null) projectedOwners.addAll(request.owners());

    if (projectedOwners.isEmpty()) {
      throw new LayerManagerException(
          LayerExceptionCodes.LAYER_REQUIRES_OWNER, HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public Map<UUID, LayerRole> addLayerMembers(UUID layerId, Map<UUID, LayerRole> userRoles) {
    try {
      return layerMemberRepository.addLayerMembers(layerId, userRoles);
    } catch (RepositoryException e) {
      throw new LayerManagerException(
          e.getCode(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void removeLayerMembers(UUID layerId, Set<UUID> userIds) {
    // Check if this would orphan the layer (remove all owners)
    var projectedOwners = new HashSet<>(layerMemberRepository.getOwnerUserIds(layerId));
    projectedOwners.removeAll(userIds);

    if (projectedOwners.isEmpty()) {
      throw new LayerManagerException(
          LayerExceptionCodes.LAYER_REQUIRES_OWNER, HttpStatus.BAD_REQUEST);
    }

    layerMemberRepository.removeLayerMembers(layerId, userIds);
  }

  @Override
  public Set<SourceLayer> listLayerServers(UUID layerId) {
    return this.serverLayerRepository.getLayerServers(layerId);
  }
}
