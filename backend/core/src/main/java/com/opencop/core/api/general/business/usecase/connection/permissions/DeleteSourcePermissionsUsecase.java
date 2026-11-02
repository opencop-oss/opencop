// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.permissions;

import com.opencop.core.api.general.ports.usecase.connection.permissions.DeleteSourcePermissions;
import com.opencop.core.api.iam.ports.gateway.repository.ServerLayerRepository;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes source permissions directly from repositories.
 *
 * <p>This bypasses ServerManager.delete() which requires the data source to exist. When deleting a
 * data source, we want to clean up permissions even if the data source is already gone.
 */
public class DeleteSourcePermissionsUsecase implements DeleteSourcePermissions {

  private static final Logger log = LoggerFactory.getLogger(DeleteSourcePermissionsUsecase.class);

  private final ServerLayerRepository serverLayerRepository;
  private final ServerUserRepository serverUserRepository;

  public DeleteSourcePermissionsUsecase(
      ServerLayerRepository serverLayerRepository, ServerUserRepository serverUserRepository) {
    this.serverLayerRepository = serverLayerRepository;
    this.serverUserRepository = serverUserRepository;
  }

  @Override
  public void deletePermissions(UUID sourceId) {
    log.debug("Deleting permissions for source {}", sourceId);
    serverLayerRepository.delete(sourceId);
    serverUserRepository.delete(sourceId);
  }
}
