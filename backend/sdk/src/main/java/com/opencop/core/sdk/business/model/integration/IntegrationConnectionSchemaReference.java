// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import java.util.Objects;
import java.util.UUID;

/** Provider-neutral reference to one exact connection schema version of an integration. */
public record IntegrationConnectionSchemaReference(UUID integrationId, int schemaVersion) {

  public IntegrationConnectionSchemaReference {
    Objects.requireNonNull(integrationId, "integrationId");
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
  }
}
