// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Egress mappers an integration permits a connection to write with. An open rule permits any
 * available egress mapper; otherwise only the listed mappers are permitted. The caller still checks
 * that the mapper is contributed, is an egress mapper, and is not archived.
 *
 * @param allowedMappers egress mappers permitted by identity
 * @param open whether any available egress mapper is permitted
 */
public record EgressMapperRule(Set<UUID> allowedMappers, boolean open) {

  public EgressMapperRule {
    allowedMappers = Set.copyOf(Objects.requireNonNull(allowedMappers, "allowedMappers"));
    // An empty closed rule would permit no writing at all; an integration that cannot write
    // declares no egress rule instead.
    if (allowedMappers.isEmpty() && !open) {
      throw new IllegalArgumentException("At least one egress mapper rule is required");
    }
  }

  /** Permits any available egress mapper. */
  public static EgressMapperRule openMapper() {
    return new EgressMapperRule(Set.of(), true);
  }

  /** Permits only the listed egress mappers. */
  public static EgressMapperRule closedTo(Set<UUID> allowedMappers) {
    return new EgressMapperRule(allowedMappers, false);
  }

  /** Checks the plugin's rule for a selected egress mapper. */
  public boolean allows(UUID mapperId) {
    return mapperId != null && (open || allowedMappers.contains(mapperId));
  }
}
