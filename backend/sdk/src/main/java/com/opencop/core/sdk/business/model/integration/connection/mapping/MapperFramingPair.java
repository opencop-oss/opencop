// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.mapping;

import java.util.Objects;
import java.util.UUID;

/**
 * Permits exactly this mapper with exactly this framing. Mapper identity and framing identity only;
 * mapper versions have an independent lifecycle.
 */
public record MapperFramingPair(UUID mapperId, UUID framingId) implements FramingRule {
  public MapperFramingPair {
    Objects.requireNonNull(mapperId, "mapperId");
    Objects.requireNonNull(framingId, "framingId");
  }
}
