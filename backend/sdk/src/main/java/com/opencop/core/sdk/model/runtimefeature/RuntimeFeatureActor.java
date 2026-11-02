// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RuntimeFeatureActor(
    UUID userId, Optional<String> fullName, Optional<String> email, boolean globalAdmin) {

  private static final int MAX_FULL_NAME_LENGTH = 255;
  private static final int MAX_EMAIL_LENGTH = 320;

  public RuntimeFeatureActor {
    Objects.requireNonNull(userId, "userId is required");
    fullName =
        sanitize(
            Objects.requireNonNull(fullName, "fullName is required").orElse(null),
            MAX_FULL_NAME_LENGTH);
    email =
        sanitize(Objects.requireNonNull(email, "email is required").orElse(null), MAX_EMAIL_LENGTH);
  }

  private static Optional<String> sanitize(String value, int maxLength) {
    if (value == null) {
      return Optional.empty();
    }
    var withoutControls = value.replaceAll("\\p{Cntrl}", "").trim();
    if (withoutControls.isEmpty()) {
      return Optional.empty();
    }
    var end =
        withoutControls.offsetByCodePoints(
            0, Math.min(withoutControls.codePointCount(0, withoutControls.length()), maxLength));
    return Optional.of(withoutControls.substring(0, end));
  }
}
