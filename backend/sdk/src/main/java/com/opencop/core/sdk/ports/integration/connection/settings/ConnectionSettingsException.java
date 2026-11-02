// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.settings;

import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import java.util.List;
import java.util.Objects;

/** Rejects a complete settings document that breaks one or more of the integration's rules. */
public final class ConnectionSettingsException extends Exception {

  private final List<SettingsViolation> violations;

  public ConnectionSettingsException(List<SettingsViolation> violations) {
    super(summary(violations));
    this.violations = List.copyOf(violations);
  }

  public List<SettingsViolation> violations() {
    return violations;
  }

  private static String summary(List<SettingsViolation> violations) {
    Objects.requireNonNull(violations, "violations");
    if (violations.isEmpty()) {
      throw new IllegalArgumentException("violations must not be empty");
    }
    return violations.stream()
        .map(violation -> violation.path() + ": " + violation.message())
        .reduce((left, right) -> left + "; " + right)
        .orElseThrow();
  }
}
