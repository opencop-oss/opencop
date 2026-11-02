// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection.extension;

import java.util.UUID;

/**
 * Counts what a deleted connection's extensions left behind, which an operator may need to follow
 * up: a delete hook that did not complete or never ran, and a secret entry left in the store. An
 * extension is named by its integration and identifier, which is unique only within that
 * integration.
 */
public interface DeleteHookTelemetry {

  void recordFailure(UUID integrationId, String extensionId);

  void recordTimeout(UUID integrationId, String extensionId);

  /** The extension's hook never ran: what it needed could not be read, or it was interrupted. */
  void recordNotRun(UUID integrationId, String extensionId);

  /** The extension's secret entry could not be deleted, so it stays in the secret store. */
  void recordSecretEntryNotDeleted(UUID integrationId, String extensionId);
}
