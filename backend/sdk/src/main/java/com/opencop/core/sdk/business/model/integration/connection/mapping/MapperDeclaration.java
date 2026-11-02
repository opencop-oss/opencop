// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mapper choices permitted by one integration schema for a protocol that does not frame its stream.
 *
 * <p>An open rule lets the plugin accept any available, eligible mapper; otherwise only the listed
 * mappers are permitted. The caller still checks mapper availability, archival state, and
 * access-mode eligibility.
 *
 * @param allowedMappers mappers permitted by identity
 * @param open whether any available mapper is permitted
 */
public record MapperDeclaration(Set<UUID> allowedMappers, boolean open)
    implements ConnectionMappingDeclaration {

  public MapperDeclaration {
    allowedMappers = Set.copyOf(Objects.requireNonNull(allowedMappers, "allowedMappers"));
    // An empty declaration would leave the plugin with no valid connection setup.
    if (allowedMappers.isEmpty() && !open) {
      throw new IllegalArgumentException("At least one mapper rule is required");
    }
  }

  /** Permits any available mapper. */
  public static MapperDeclaration openMapper() {
    return new MapperDeclaration(Set.of(), true);
  }

  /** Permits only the listed mappers. */
  public static MapperDeclaration closedTo(Set<UUID> allowedMappers) {
    return new MapperDeclaration(allowedMappers, false);
  }

  /** Checks the plugin's rule for a selected mapper. */
  public boolean allows(UUID mapperId) {
    return mapperId != null && (open || allowedMappers.contains(mapperId));
  }
}
