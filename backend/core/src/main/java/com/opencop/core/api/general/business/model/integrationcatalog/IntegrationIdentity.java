// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Objects;
import java.util.UUID;

/**
 * An integration as the catalog records it: its identity, display metadata and activation.
 *
 * @param active whether new connections may select this integration
 */
public record IntegrationIdentity(UUID id, String name, String description, boolean active) {

  public IntegrationIdentity {
    Objects.requireNonNull(id, "id");
    requireNonBlank(name, "name");
    requireNonBlank(description, "description");
  }

  /** The same integration with other display metadata and unchanged activation. */
  public IntegrationIdentity withDisplayMetadata(String name, String description) {
    return new IntegrationIdentity(id, name, description, active);
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
