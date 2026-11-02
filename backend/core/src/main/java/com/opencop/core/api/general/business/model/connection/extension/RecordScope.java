// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.extension;

import java.util.Objects;
import java.util.UUID;

/**
 * The records one extension may reach: those of one connection, written for its integration, under
 * the extension's own identifier.
 */
public record RecordScope(UUID sourceId, UUID integrationId, String extensionId) {

  public RecordScope {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(integrationId, "integrationId");
    Objects.requireNonNull(extensionId, "extensionId");
  }
}
