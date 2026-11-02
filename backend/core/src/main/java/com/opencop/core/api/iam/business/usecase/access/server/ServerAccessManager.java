// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.business.usecase.access.server;

import static com.opencop.core.api.iam.ports.usecase.access.server.ServerAccessExceptionCodes.EMPTY_SERVER_ID;
import static com.opencop.core.api.iam.ports.usecase.access.server.ServerAccessExceptionCodes.FAILED_TO_UPDATE_LAYERS;
import static com.opencop.core.api.iam.ports.usecase.access.server.ServerAccessExceptionCodes.SERVER_ALREADY_EXISTS;
import static com.opencop.core.api.iam.ports.usecase.access.server.ServerAccessExceptionCodes.SERVER_DOES_NOT_EXISTS;
import static com.opencop.core.api.iam.ports.usecase.access.server.ServerAccessExceptionCodes.USER_NOT_DIRECT_MEMBER;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.presenter.Presenter;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManagerException;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceLayer;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.model.iam.source.UpdateSourcePermissionsRequest;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class ServerAccessManager<S> implements ServerManager<S> {

  private static final Logger log = LoggerFactory.getLogger(ServerAccessManager.class);

  private final Presenter<S, Server> serverPresenter;
  private final DataSourceRepository dataSourceRepository;
  private final ServerLayerRepository serverLayerRepository;
  private final ServerUserRepository serverUserRepository;
  private final UserService userService;

  ServerAccessManager(
      final Presenter<S, Server> serverPresenter,
      final DataSourceRepository dataSourceRepository,
      final ServerLayerRepository serverLayerRepository,
      final ServerUserRepository serverUserRepository,
      final UserService userService) {
    this.serverPresenter = serverPresenter;
    this.dataSourceRepository = dataSourceRepository;
    this.serverLayerRepository = serverLayerRepository;
    this.serverUserRepository = serverUserRepository;
    this.userService = userService;
  }

  @Override
  public S add(UUID serverId, Set<UUID> layersId, Set<UUID> userIds, UUID creatorUserId)
      throws ServerManagerException {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    // Check if permissions already exist (prevents duplicate permission records)
    // I'm not sure exactly re. how important this is but if it's essential it should be DB
    // constraint instead.
    if (this.serverLayerRepository.serverExists(serverId)
        || this.serverUserRepository.serverExists(serverId)) {
      throw new ServerManagerException(SERVER_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
    }

    if (layersId != null) {
      layersId.forEach(
          layerId -> {
            try {
              serverLayerRepository.add(serverId, layerId, ServerRole.OWNER);
            } catch (RepositoryException e) {
              throw new ServerManagerException(
                  e.getCode(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
          });
    }

    // NOTE: creatorPrincipal (from IAM's auth token) is intentionally ignored.
    // Core extracts the real user principal and passes it in usersPrincipal.
    // When IAM is merged into Core, Core will continue to extract the user
    // and pass it directly to the repository - this parameter can be removed.
    userIds.forEach(userId -> serverUserRepository.add(serverId, userId, ServerRole.OWNER));

    return serverPresenter.present(new Server.Builder().withServerId(serverId).build());
  }

  @Override
  public Set<UUID> list() {
    var layers = serverLayerRepository.list();
    Set<UUID> servers = new HashSet<>();
    layers.forEach((serverId, _) -> servers.add(serverId));

    var users = serverUserRepository.list();
    users.forEach((serverId, _) -> servers.add(serverId));

    return servers;
  }

  @Override
  public void delete(UUID serverId) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    serverLayerRepository.delete(serverId);
    serverUserRepository.delete(serverId);
  }

  /**
   * Check that the data source exists in the database. We use to check the permissions table but
   * this seems better for handling recovering orphaned / permission-less data sources.
   */
  private void requireDataSourceExists(UUID serverId) {
    if (dataSourceRepository.findById(serverId).isEmpty()) {
      throw new ServerManagerException(SERVER_DOES_NOT_EXISTS, HttpStatus.BAD_REQUEST);
    }
  }

  @Override
  public Set<SourceLayer> getLayers(UUID serverId) {
    requireDataSourceExists(serverId);
    return serverLayerRepository.getLayers(serverId);
  }

  @Override
  public void updateLayers(UUID serverId, UpdateSourcePermissionsRequest request) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    try {
      // TODO verify that each layer exists
      this.serverLayerRepository.addLayers(serverId, request.viewers(), ServerRole.VIEWER);
      this.serverLayerRepository.addLayers(serverId, request.admins(), ServerRole.ADMIN);
      this.serverLayerRepository.addLayers(serverId, request.owners(), ServerRole.OWNER);
    } catch (RepositoryException _) {
      throw new ServerManagerException(FAILED_TO_UPDATE_LAYERS, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void deleteLayers(UUID serverId, Set<UUID> layerIds) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    this.serverLayerRepository.removeLayers(serverId, layerIds);
  }

  @Override
  public Set<SourceUser> getUsers(UUID serverId) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    Set<SourceUser> sourceUsers = serverUserRepository.getUsers(serverId);
    Set<SourceUser> users = new HashSet<>();
    sourceUsers.forEach(
        serverUser -> {
          try {
            var user = userService.get(serverUser.getUserId());
            serverUser.setFirstName(user.firstName());
            serverUser.setLastName(user.lastName());
            serverUser.setUsername(user.username());
            users.add(serverUser);
          } catch (IdpException _) {
            log.warn("Can't find user with id {}", serverUser.getUserId());
          }
        });
    return users;
  }

  @Override
  public Map<UUID, ServerRole> updateUsers(UUID serverId, UpdateSourcePermissionsRequest request) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    // Unknown ids are skipped rather than rejected, so the applied map is what actually landed.
    var applied = new LinkedHashMap<UUID, ServerRole>();
    this.serverUserRepository
        .addUsers(serverId, request.viewers(), ServerRole.VIEWER)
        .forEach(id -> applied.put(id, ServerRole.VIEWER));
    this.serverUserRepository
        .addUsers(serverId, request.admins(), ServerRole.ADMIN)
        .forEach(id -> applied.put(id, ServerRole.ADMIN));
    this.serverUserRepository
        .addUsers(serverId, request.owners(), ServerRole.OWNER)
        .forEach(id -> applied.put(id, ServerRole.OWNER));
    return applied;
  }

  @Override
  public void deleteUsers(UUID serverId, Set<UUID> userIds) {
    if (serverId == null) {
      throw new ServerManagerException(EMPTY_SERVER_ID, HttpStatus.BAD_REQUEST);
    }
    requireDataSourceExists(serverId);
    // Check that all principals have direct access before deleting.
    // Users may have indirect access via a layer, which cannot be removed here.
    Set<UUID> directUserIds = this.serverUserRepository.getUserIdsForServer(serverId);
    for (UUID userId : userIds) {
      if (!directUserIds.contains(userId)) {
        throw new ServerManagerException(USER_NOT_DIRECT_MEMBER, HttpStatus.BAD_REQUEST);
      }
    }
    this.serverUserRepository.removeUsers(serverId, userIds);
  }
}
