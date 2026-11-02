// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import java.util.Objects;

public record CurrentBrokerProvider(String name) {

  public CurrentBrokerProvider {
    Objects.requireNonNull(name, "name is required");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
