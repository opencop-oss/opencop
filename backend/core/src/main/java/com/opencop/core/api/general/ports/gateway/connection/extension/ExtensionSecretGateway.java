// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection.extension;

import java.util.Map;
import java.util.UUID;

/**
 * Stores each extension's own secret entry on a connection, where the host alone decides: the
 * connection, the integration and the extension name it, nothing the extension passes does.
 */
public interface ExtensionSecretGateway {

  /** The entry's secrets, empty when nothing is stored. */
  Map<String, String> read(UUID sourceId, UUID integrationId, String extensionId);

  /** Replaces the entry's secrets whole with a non-empty document. */
  void replace(UUID sourceId, UUID integrationId, String extensionId, Map<String, String> secrets);

  /** Deletes the entry; deleting a missing one does nothing. */
  void delete(UUID sourceId, UUID integrationId, String extensionId);
}
