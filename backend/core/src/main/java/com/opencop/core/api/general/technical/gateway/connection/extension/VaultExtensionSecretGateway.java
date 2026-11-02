// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.connection.extension;

import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps each extension's secret entry in the secret store, beside the connection's own material,
 * under a path built from the connection, the integration and the extension alone. Two integrations
 * declaring one extension name never share an entry.
 */
@Component
public class VaultExtensionSecretGateway implements ExtensionSecretGateway {

  private static final String ENTRY_PATH = "opencop/shared/connect/%s/extensions/%s/%s";

  private final SecretStore secretStore;

  VaultExtensionSecretGateway(SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public Map<String, String> read(UUID sourceId, UUID integrationId, String extensionId) {
    var stored = secretStore.read(path(sourceId, integrationId, extensionId));
    return stored == null ? Map.of() : Map.copyOf(stored);
  }

  /**
   * Writes the new secrets and drops the ones the replacement no longer holds, in one patch. The
   * dropped keys come from a read before the patch, so a key another writer adds in between
   * survives: one invocation writing the whole document is the expected use.
   */
  @Override
  public void replace(
      UUID sourceId, UUID integrationId, String extensionId, Map<String, String> secrets) {
    var path = path(sourceId, integrationId, extensionId);
    var dropped = new HashSet<>(read(sourceId, integrationId, extensionId).keySet());
    dropped.removeAll(secrets.keySet());
    secretStore.patch(path, secrets, dropped);
  }

  @Override
  public void delete(UUID sourceId, UUID integrationId, String extensionId) {
    secretStore.deleteAllVersions(path(sourceId, integrationId, extensionId));
  }

  private static String path(UUID sourceId, UUID integrationId, String extensionId) {
    return ENTRY_PATH.formatted(sourceId, integrationId, extensionId);
  }
}
