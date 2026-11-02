// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.extension.RegisteredExtension;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletes the secret entries a connection's extensions keep once none of them may read theirs
 * again: when the connection is deleted, or leaves the integration whose extensions wrote them.
 *
 * <p>An entry belongs to the integration, not to one of its schema versions, so the entries of
 * every extension a registered version of the integration declares are deleted, an extension a
 * newer version dropped included.
 */
public class ExtensionSecretEntries {

  private static final Logger log = LoggerFactory.getLogger(ExtensionSecretEntries.class);

  private final ConnectionExtensionRegistry extensions;
  private final ExtensionSecretGateway extensionSecrets;
  private final ConnectionLock lock;
  private final DeleteHookTelemetry telemetry;

  public ExtensionSecretEntries(
      ConnectionExtensionRegistry extensions,
      ExtensionSecretGateway extensionSecrets,
      ConnectionLock lock,
      DeleteHookTelemetry telemetry) {
    this.extensions = extensions;
    this.extensionSecrets = extensionSecrets;
    this.lock = lock;
    this.telemetry = telemetry;
  }

  /**
   * Deletes the integration's entries on the connection, unless the connection, still active, binds
   * to the integration again: it then came back since it left, and an entry may have been written
   * since. The deletion holds the connection's row lock, which a return and an entry write both
   * wait on.
   *
   * <p>An entry that cannot be deleted is left behind, and counted, without failing the caller; an
   * integration that does not run here declares nothing, so its extensions' entries stay.
   */
  public void retire(UUID sourceId, UUID integrationId) {
    var known = knownExtensionIds(integrationId);
    if (known.isEmpty()) {
      return;
    }
    try {
      lock.holding(
          sourceId,
          connection -> {
            if (connection.archivedAt() == null
                && ConnectionIntegrationReferencePolicy.bindsTo(
                    connection.integration(), integrationId)) {
              log.info(
                  "Secret entries of integration {} kept for connection {}: it binds to the"
                      + " integration again",
                  integrationId,
                  sourceId);
              return null;
            }
            known.forEach(extensionId -> delete(sourceId, integrationId, extensionId));
            return null;
          });
    } catch (RuntimeException unlocked) {
      log.warn(
          "Secret entries of integration {} could not be deleted for connection {}",
          integrationId,
          sourceId,
          unlocked);
      known.forEach(
          extensionId -> telemetry.recordSecretEntryNotDeleted(integrationId, extensionId));
    }
  }

  private List<String> knownExtensionIds(UUID integrationId) {
    return extensions.all().stream()
        .filter(extension -> extension.schema().reference().integrationId().equals(integrationId))
        .map(RegisteredExtension::id)
        .distinct()
        .toList();
  }

  private void delete(UUID sourceId, UUID integrationId, String extensionId) {
    try {
      extensionSecrets.delete(sourceId, integrationId, extensionId);
    } catch (RuntimeException undeletable) {
      log.warn(
          "Secret entry of extension {} of integration {} could not be deleted for connection {}",
          extensionId,
          integrationId,
          sourceId,
          undeletable);
      telemetry.recordSecretEntryNotDeleted(integrationId, extensionId);
    }
  }
}
