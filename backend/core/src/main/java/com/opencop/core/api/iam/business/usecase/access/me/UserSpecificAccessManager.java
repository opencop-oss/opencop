// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.me;

import com.opencop.core.api.iam.business.usecase.access.layer.EffectiveLayerAccessService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificException;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificExceptionCodes;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class UserSpecificAccessManager implements UserSpecificManager {

  private static final Logger log = LoggerFactory.getLogger(UserSpecificAccessManager.class);
  private final ServerLayerRepository serverLayerRepository;
  private final ServerUserRepository serverUserRepository;
  private final EffectiveLayerAccessService effectiveLayerAccessService;
  private final SourceAccessRepository sourceAccessRepository;

  UserSpecificAccessManager(
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository serverUserRepository,
      final EffectiveLayerAccessService effectiveLayerAccessService,
      final SourceAccessRepository sourceAccessRepository) {
    this.serverLayerRepository = serverLayerRepository;
    this.serverUserRepository = serverUserRepository;
    this.effectiveLayerAccessService = effectiveLayerAccessService;
    this.sourceAccessRepository = sourceAccessRepository;
  }

  @Override
  public List<SourceRole> getLayerInfo(UUID userId, UUID layerId) throws UserSpecificException {
    if (effectiveLayerAccessService.getEffectiveRole(layerId, userId).isEmpty()) {
      log.info("User {} attempted to access layer {} without permission", userId, layerId);
      throw new UserSpecificException(
          UserSpecificExceptionCodes.LAYER_ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    List<SourceRole> sourceInfos = new ArrayList<>();
    var layerServers = this.serverLayerRepository.getLayerServers(layerId);
    layerServers.forEach(
        layerServer ->
            sourceInfos.add(
                new SourceRole(
                    layerServer.getServerId(), layerServer.getRole(), AccessVia.INDIRECT)));
    return sourceInfos;
  }

  @Override
  public List<LayerInfo> getUserLayers(UUID userId) throws UserSpecificException {
    return effectiveLayerAccessService.getEffectiveLayers(userId);
  }

  @Override
  public List<LayerInfo> getUserLayers(UUID userId, RecordVisibility recordVisibility)
      throws UserSpecificException {
    return effectiveLayerAccessService.getEffectiveLayers(userId, recordVisibility);
  }

  @Override
  public List<SourceRole> getUserServers(UUID userId) throws UserSpecificException {
    return sourceAccessRepository.userRoles(userId);
  }

  @Override
  public List<SourceRole> getUserDirectServers(UUID userId) throws UserSpecificException {
    return serverUserRepository.getServersForUser(userId).stream()
        .map(server -> new SourceRole(server.getServerId(), server.getRole(), AccessVia.DIRECT))
        .toList();
  }
}
