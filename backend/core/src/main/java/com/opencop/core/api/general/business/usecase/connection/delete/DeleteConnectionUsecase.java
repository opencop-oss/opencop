// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.delete;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.service.ConnectionArchiveLifecycleService;
import com.opencop.core.api.general.business.usecase.connection.extension.ConnectionDeleteHooks;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.usecase.connection.delete.DeleteConnection;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deletes a data source and its associated resources (Connect connection or Kafka topic). */
public class DeleteConnectionUsecase implements DeleteConnection {

  private static final Logger log = LoggerFactory.getLogger(DeleteConnectionUsecase.class);
  private final ConnectionAuthorization authorization;
  private final ServerManager<Server> serverManager;
  private final ConnectionArchiveLifecycleService archiveLifecycle;
  private final ConnectionDeleteHooks deleteHooks;
  private final ConnectionLock lock;

  public DeleteConnectionUsecase(
      ConnectionAuthorization authorization,
      ServerManager<Server> serverManager,
      ConnectionArchiveLifecycleService archiveLifecycle,
      ConnectionDeleteHooks deleteHooks,
      ConnectionLock lock) {
    this.authorization = authorization;
    this.serverManager = serverManager;
    this.archiveLifecycle = archiveLifecycle;
    this.deleteHooks = deleteHooks;
    this.lock = lock;
  }

  @Override
  public Set<SourceUser> delete(UUID sourceId, OpenCopPrincipal authentication) {
    var userId = authentication.internalUserId();
    log.info("Request to delete source {} initiated by {}", sourceId, userId);

    // Archiving is a lifecycle operation, authorized exactly like start, stop, and patch: a direct
    // OWNER grant on the source, or global administration. Layer-derived ownership is not enough.
    authorization.assertDirectOwnerOrAdmin(sourceId, userId, authentication.isGlobalAdmin());

    // The connection's extensions read what they need before the archive deletes its secrets, and
    // release what they hold only once the archive committed, so a failed archive releases nothing.
    // The row lock is held from that read to the archive, so a setup replacing the connection's
    // waits and is then refused: the hooks read the setup that is archived.
    var afterDeletion =
        lock.holding(
            sourceId,
            _ -> {
              var hooks = deleteHooks.prepare(sourceId, userId);
              archiveLifecycle.archive(sourceId, Caller.of(authentication));
              return hooks;
            });
    afterDeletion.run();

    // Resolved after the archive so an unknown source is reported by the lifecycle's own not-found
    // check rather than by this lookup. Archiving does not remove access rows, and the source stays
    // readable by id, so the recipient list is the same either side of the call.
    return serverManager.getUsers(sourceId);
  }
}
