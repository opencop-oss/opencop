// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.service;

import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.iam.ports.gateway.repository.RepositoryException;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

// TODO move to a policy and inject the service instead of jpa repository
public class ConnectionAuthorizationService implements ConnectionAuthorization {

  private static final Logger log = LoggerFactory.getLogger(ConnectionAuthorizationService.class);
  private static final String ERROR_MESSAGE =
      "You are not authorized for the specified change to this data source";

  private final DataSourceRepository dataSourceRepository;
  private final ServerUserRepository serverUserRepository;
  private final SourceRelationshipAuthorizationService sourceAuthorization;

  public ConnectionAuthorizationService(
      DataSourceRepository dataSourceRepository,
      ServerUserRepository serverUserRepository,
      SourceRelationshipAuthorizationService sourceAuthorization) {
    this.dataSourceRepository = dataSourceRepository;
    this.serverUserRepository = serverUserRepository;
    this.sourceAuthorization = sourceAuthorization;
  }

  @Override
  public void assertCanRead(UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    if (sourceAuthorization.canReadSource(userId, sourceId, isGlobalAdmin)) {
      return;
    }
    throw ConnectionManagementException.connectionNotFound(sourceId);
  }

  @Override
  public boolean isDirectOwnerOrAdmin(UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    return isGlobalAdmin || hasDirectOwnerRole(sourceId, userId);
  }

  @Override
  public void assertDirectOwnerOrAdmin(UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    if (isGlobalAdmin) {
      return;
    }

    assertDataSourceExists(sourceId);

    if (hasDirectOwnerRole(sourceId, userId)) {
      return;
    }

    log.warn(
        "User {} denied access to source {} (requires direct OWNER or global admin)",
        userId,
        sourceId);
    throw new AccessDeniedException(ERROR_MESSAGE);
  }

  /**
   * Only a direct OWNER row counts: layer-derived ownership elsewhere does not. A user without
   * direct access rows has no owner role.
   */
  private boolean hasDirectOwnerRole(UUID sourceId, UUID userId) {
    try {
      return serverUserRepository.get(sourceId, userId).stream()
          .anyMatch(sourceUser -> sourceUser.getRole() == ServerRole.OWNER);
    } catch (RepositoryException _) {
      return false;
    }
  }

  @Override
  public void assertDirectOwnerAdminOrGlobalAdmin(
      UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    if (isGlobalAdmin) {
      return;
    }

    assertDataSourceExists(sourceId);

    try {
      var hasDirectLifecycleRole =
          serverUserRepository.get(sourceId, userId).stream()
              .anyMatch(
                  sourceUser ->
                      sourceUser.getRole() == ServerRole.OWNER
                          || sourceUser.getRole() == ServerRole.ADMIN);
      if (hasDirectLifecycleRole) {
        return;
      }
    } catch (RepositoryException e) {
      // Mirrors assertDirectOwnerOrAdmin(...) and today's ServerUserAccessService#get(...):
      // RepositoryException means no direct source role, while unchecked repository/DB failures
      // bubble. If that repository contract starts wrapping broader lookup failures, revisit this
      // so restart failures do not become misleading 403s.
      log.warn(
          "Direct restart access lookup for user {} on source {} returned repository exception {}; denying as direct-role miss",
          userId,
          sourceId,
          e.getCode(),
          e);
    }

    log.warn(
        "User {} denied restart access to source {} (requires direct ADMIN/OWNER or global admin)",
        userId,
        sourceId);
    throw new AccessDeniedException(ERROR_MESSAGE);
  }

  private void assertDataSourceExists(UUID sourceId) {
    if (dataSourceRepository.findById(sourceId).isEmpty()) {
      throw new GlobalOpenCopException("Data source not found: " + sourceId, HttpStatus.NOT_FOUND);
    }
  }
}
