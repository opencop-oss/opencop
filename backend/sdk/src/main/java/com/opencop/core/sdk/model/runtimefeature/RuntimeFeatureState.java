// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.time.Instant;
import java.util.Objects;

public record RuntimeFeatureState(
    String name,
    RuntimeFeatureConsumerClass consumerClass,
    RuntimeFeatureAudience audience,
    long revision,
    Instant updatedAt) {

  public RuntimeFeatureState {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    Objects.requireNonNull(consumerClass, "consumerClass is required");
    Objects.requireNonNull(audience, "audience is required");
    if (revision < 0) {
      throw new IllegalArgumentException("revision must not be negative");
    }
    Objects.requireNonNull(updatedAt, "updatedAt is required");
  }
}
