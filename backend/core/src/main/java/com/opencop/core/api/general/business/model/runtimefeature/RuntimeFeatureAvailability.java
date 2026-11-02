// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.runtimefeature;

public record RuntimeFeatureAvailability(String name, boolean enabled) {
  public RuntimeFeatureAvailability {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
  }
}
