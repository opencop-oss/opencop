// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.geocoding;

import java.util.Objects;

/**
 * Checked geocoder failure carrying a host category.
 *
 * <p>The message must never contain provider URLs, query strings, user search text or credentials.
 */
public final class GeocodingException extends Exception {

  private final GeocodingFailure failure;

  public GeocodingException(GeocodingFailure failure, String safeMessage) {
    this(failure, safeMessage, null);
  }

  public GeocodingException(GeocodingFailure failure, String safeMessage, Throwable cause) {
    Objects.requireNonNull(failure, "failure");
    super(requireSafeMessage(safeMessage), cause);
    this.failure = failure;
  }

  public GeocodingFailure failure() {
    return failure;
  }

  private static String requireSafeMessage(String message) {
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("safeMessage must not be blank");
    }
    return message;
  }
}
