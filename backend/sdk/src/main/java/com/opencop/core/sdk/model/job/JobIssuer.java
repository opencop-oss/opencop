// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import java.util.Objects;
import java.util.UUID;

public record JobIssuer(JobIssuerType type, String id) {

  public JobIssuer {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
  }

  public static JobIssuer user(UUID userId) {
    Objects.requireNonNull(userId, "userId");
    return new JobIssuer(JobIssuerType.USER, userId.toString());
  }

  public static JobIssuer system(String systemId) {
    return new JobIssuer(JobIssuerType.SYSTEM, systemId);
  }
}
