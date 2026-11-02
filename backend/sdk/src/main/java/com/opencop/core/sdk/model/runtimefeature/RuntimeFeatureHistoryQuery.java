// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.time.Instant;
import java.util.UUID;

/** Structured, Runtime Feature-local query for successful-action history. */
public record RuntimeFeatureHistoryQuery(
    String featureName,
    RuntimeFeatureHistoryEntry.Operation operation,
    UUID actorUserId,
    Instant occurredFrom,
    Instant occurredTo,
    int page,
    int size) {

  public static final int MAX_PAGE_SIZE = 100;

  public RuntimeFeatureHistoryQuery {
    if (featureName != null && (featureName.isBlank() || featureName.length() > 64)) {
      throw new IllegalArgumentException("featureName must contain between 1 and 64 characters");
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be non-negative");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
    }
    if ((long) page * size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("page offset exceeds the supported range");
    }
    if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
      throw new IllegalArgumentException("occurredFrom must not be after occurredTo");
    }
  }
}
