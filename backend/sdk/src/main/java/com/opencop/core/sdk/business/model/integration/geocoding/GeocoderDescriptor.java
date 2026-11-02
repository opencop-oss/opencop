// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

import java.util.Objects;
import java.util.UUID;

/** Declarative display metadata for a geocoder. */
public record GeocoderDescriptor(UUID id, String label, String description) {

  public GeocoderDescriptor {
    Objects.requireNonNull(id, "id");
    requireNonBlank(label, "label");
    requireNonBlank(description, "description");
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
