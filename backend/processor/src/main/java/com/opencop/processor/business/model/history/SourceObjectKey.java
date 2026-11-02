// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import java.util.Objects;
import java.util.UUID;

/**
 * Compound key that uniquely identifies an object within a specific source. Used internally by the
 * sampler, coordinator sequential state, and payload builder to prevent cross-source object-id
 * collisions — objects with the same id string from different sources are distinct entities.
 */
public record SourceObjectKey(UUID source, String objectId) {
  public SourceObjectKey {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(objectId, "objectId must not be null");
  }
}
