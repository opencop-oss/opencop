// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.permissions;

import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import java.util.Set;
import java.util.UUID;

public class AddCreateSourcePermissions implements CreateSourcePermissions {

  private final ServerManager<Server> serverManager;

  public AddCreateSourcePermissions(final ServerManager<Server> serverManager) {
    this.serverManager = serverManager;
  }

  @Override
  public void createPermissions(UUID sourceId, UUID userId) {
    try {
      serverManager.add(sourceId, Set.of(), Set.of(userId), userId);
    } catch (RuntimeException _) {
      // Permission creation failures are currently ignored and the connection is not rolled back.
    }
  }
}
