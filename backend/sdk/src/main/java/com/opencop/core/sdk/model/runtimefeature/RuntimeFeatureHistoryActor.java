// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.Objects;
import java.util.UUID;

public record RuntimeFeatureHistoryActor(
    UUID userId, String fullName, String email, boolean globalAdmin) {

  public RuntimeFeatureHistoryActor {
    Objects.requireNonNull(userId, "userId is required");
  }
}
