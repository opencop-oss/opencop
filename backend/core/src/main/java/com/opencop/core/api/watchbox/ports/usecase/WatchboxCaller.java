// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.ports.usecase;

import java.util.UUID;

public record WatchboxCaller(UUID userId, boolean globalAdmin, String fullName, String email) {

  private static final int MAX_FULL_NAME_LENGTH = 255;
  private static final int MAX_EMAIL_LENGTH = 320;

  public WatchboxCaller {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }
    fullName = sanitize(fullName, MAX_FULL_NAME_LENGTH);
    email = sanitize(email, MAX_EMAIL_LENGTH);
  }

  public WatchboxCaller(UUID userId, boolean globalAdmin) {
    this(userId, globalAdmin, null, null);
  }

  private static String sanitize(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    String withoutControls = value.replaceAll("\\p{Cntrl}", "").trim();
    if (withoutControls.isEmpty()) {
      return null;
    }
    int end =
        withoutControls.offsetByCodePoints(
            0, Math.min(withoutControls.codePointCount(0, withoutControls.length()), maxLength));
    return withoutControls.substring(0, end);
  }
}
