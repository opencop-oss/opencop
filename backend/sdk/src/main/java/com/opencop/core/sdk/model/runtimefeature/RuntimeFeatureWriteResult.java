// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.Objects;

public sealed interface RuntimeFeatureWriteResult
    permits RuntimeFeatureWriteResult.Applied,
        RuntimeFeatureWriteResult.Idempotent,
        RuntimeFeatureWriteResult.Unknown,
        RuntimeFeatureWriteResult.Stale {

  record Applied(RuntimeFeatureState state, RuntimeFeatureAudience previousAudience)
      implements RuntimeFeatureWriteResult {
    public Applied {
      Objects.requireNonNull(state, "state is required");
      Objects.requireNonNull(previousAudience, "previousAudience is required");
    }
  }

  record Idempotent(RuntimeFeatureState state) implements RuntimeFeatureWriteResult {
    public Idempotent {
      Objects.requireNonNull(state, "state is required");
    }
  }

  record Unknown(String name) implements RuntimeFeatureWriteResult {
    public Unknown {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name is required");
      }
    }
  }

  record Stale(RuntimeFeatureState state) implements RuntimeFeatureWriteResult {
    public Stale {
      Objects.requireNonNull(state, "state is required");
    }
  }
}
