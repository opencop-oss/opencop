// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Objects;
import java.util.UUID;

/** One version of the connection contract an integration offers, as the catalog records it. */
public record IntegrationConnectionSchemaKey(UUID integrationId, int schemaVersion) {

  public IntegrationConnectionSchemaKey {
    Objects.requireNonNull(integrationId, "integrationId");
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
  }

  /** Reads as {@code <integration id> version <schema version>} in catalog messages. */
  @Override
  public String toString() {
    return integrationId + " version " + schemaVersion;
  }
}
