// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.time.Instant;
import java.util.Objects;

public record RuntimeFeatureHistoryEntry(
    long id,
    String featureName,
    RuntimeFeatureConsumerClass consumerClass,
    Operation operation,
    RuntimeFeatureAudience beforeAudience,
    RuntimeFeatureAudience afterAudience,
    Long revisionBefore,
    long revisionAfter,
    Instant occurredAt,
    RuntimeFeatureHistoryActor actor) {

  public RuntimeFeatureHistoryEntry {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    if (featureName == null || featureName.isBlank() || featureName.length() > 64) {
      throw new IllegalArgumentException("featureName must contain between 1 and 64 characters");
    }
    Objects.requireNonNull(consumerClass, "consumerClass is required");
    Objects.requireNonNull(operation, "operation is required");
    Objects.requireNonNull(afterAudience, "afterAudience is required");
    if (revisionAfter < 0) {
      throw new IllegalArgumentException("revisionAfter must be non-negative");
    }
    switch (operation) {
      case CREATE -> {
        if (consumerClass != RuntimeFeatureConsumerClass.FRONTEND_ONLY
            || beforeAudience != null
            || !(afterAudience instanceof RuntimeFeatureAudience.None)
            || revisionBefore != null
            || revisionAfter != 0) {
          throw new IllegalArgumentException("CREATE history shape is invalid");
        }
      }
      case PATCH -> {
        Objects.requireNonNull(beforeAudience, "PATCH beforeAudience is required");
        Objects.requireNonNull(revisionBefore, "PATCH revisionBefore is required");
        if (revisionBefore < 0
            || (beforeAudience.equals(afterAudience)
                ? revisionAfter != revisionBefore
                : revisionAfter != revisionBefore + 1)) {
          throw new IllegalArgumentException("PATCH history shape is invalid");
        }
      }
    }
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    Objects.requireNonNull(actor, "actor is required");
  }

  public enum Operation {
    /** A frontend-only runtime feature definition was registered. */
    CREATE,

    /** An accepted runtime feature audience patch was applied. */
    PATCH
  }
}
