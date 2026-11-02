// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.util.Objects;

/**
 * Outcome of reading one settings value against its field: the typed value, or the single reason it
 * is unacceptable.
 */
public record SettingsFieldReading(Object value, SettingsViolation violation) {

  public SettingsFieldReading {
    if ((value == null) == (violation == null)) {
      throw new IllegalArgumentException("A reading holds either a value or a violation");
    }
  }

  public static SettingsFieldReading accepted(Object value) {
    return new SettingsFieldReading(Objects.requireNonNull(value, "value"), null);
  }

  public static SettingsFieldReading rejected(SettingsViolation violation) {
    return new SettingsFieldReading(null, Objects.requireNonNull(violation, "violation"));
  }

  public boolean isAccepted() {
    return violation == null;
  }
}
