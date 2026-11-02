// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.server.user;

import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.iam.ports.gateway.mapper.RepositoryMapper;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryExceptionCodes;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.user.entity.ServerUserAccessEntity;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerUserAccessService implements ServerUserRepository {

  private static final Logger log = LoggerFactory.getLogger(ServerUserAccessService.class);
  private final RepositoryMapper<ServerUserAccessEntity, SourceUser> mapper;
  private final IamServerUserRepository repository;
  private final UserService userService;

  public ServerUserAccessService(
      final IamServerUserRepository repository, final UserService userService) {
    this.mapper = new ServerUserMapper();
    this.repository = repository;
    this.userService = userService;
  }

  @Override
  public boolean serverExists(UUID serverId) {
    return repository.existsByIdServerId(serverId);
  }

  @Transactional
  public SourceUser add(UUID serverId, UUID userId, ServerRole role) {

    ServerUserAccessEntity entity = new ServerUserAccessEntity(serverId, userId, role);
    var saved = repository.save(entity);
    return mapper.toModel(saved);
  }

  @Transactional
  public void remove(UUID serverId, UUID userId, ServerRole role) {
    repository.deleteOne(serverId, userId, role);
  }

  @Transactional
  public Set<SourceUser> get(UUID serverId, UUID userId) throws RepositoryException {
    var result = repository.findByServerAndUserId(serverId, userId);
    if (result == null) {
      throw new RepositoryException(RepositoryExceptionCodes.DOES_NOT_EXIST);
    }
    return result.stream().map(mapper::toSanitizedModel).collect(Collectors.toSet());
  }

  @Transactional
  public void delete(UUID serverId) {
    repository.deleteAllByServerId(serverId);
  }

  @Transactional
  public List<SourceUser> getServersForUser(UUID userId) {
    List<ServerUserAccessEntity> entities = repository.findAllServersByUserId(userId);
    return entities.stream().map(mapper::toSanitizedModel).toList();
  }

  @Transactional
  public Map<UUID, Set<SourceUser>> list() {
    var users =
        repository.findAll(
            //                PageRequest.of(page, pageSize)
            );
    Map<UUID, Set<SourceUser>> map = new HashMap<>();
    users.forEach(
        entity -> {
          if (map.containsKey(entity.getServerId())) {
            if (map.get(entity.getServerId()).stream()
                .noneMatch(user -> user.getServerId().equals(entity.getServerId()))) {
              Set<SourceUser> set = new HashSet<>(map.get(entity.getServerId()));
              set.add(mapper.toSanitizedModel(entity));
              map.put(entity.getServerId(), set);
            }
          } else {
            map.put(entity.getServerId(), Set.of(mapper.toSanitizedModel(entity)));
          }
        });
    return map;
  }

  /**
   * Grants the role to each id that resolves to a real user.
   *
   * @return the ids actually granted. Unknown ids are skipped, so a caller recording what happened
   *     must use this rather than the ids it asked for.
   */
  @Transactional
  public Set<UUID> addUsers(UUID serverId, Set<UUID> userIds, ServerRole role) {
    var applied = new LinkedHashSet<UUID>();
    for (UUID userId : userIds) {
      if (!userService.exists(userId)) {
        log.warn("User with id {} not found, not adding the user to the datasource", userId);
        continue;
      }
      // An upsert, not a delete-then-insert. The pair is unique, so a role move updates the row
      // in place: deleting it first relies on the insert landing in the same flush, and Hibernate
      // orders inserts before deletes, so inside a caller's transaction the grant was lost and a
      // role change failed outright on the update that then matched no row.
      var existing = repository.findByServerAndUserId(serverId, userId);
      if (existing.isEmpty()) {
        add(serverId, userId, role);
      } else {
        existing.forEach(access -> access.setRole(role));
      }
      applied.add(userId);
    }
    return applied;
  }

  @Transactional
  public void removeUsers(UUID serverId, Set<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    repository.deleteMany(serverId, userIds);
  }

  @Transactional
  public Set<SourceUser> getUsers(UUID serverId) {
    List<ServerUserAccessEntity> users = repository.findAllByServerId(serverId);
    return users.stream().map(mapper::toSanitizedModel).collect(Collectors.toSet());
  }

  @Override
  public Set<UUID> getUserIdsForServer(UUID serverId) {
    return new HashSet<>(repository.findUserIdsByServer(serverId));
  }
}
