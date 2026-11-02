// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

import java.util.List;
import java.util.Objects;

/** Result of resolving a connection's stored or submitted settings. */
public sealed interface SettingsResolution {

  /**
   * The settings were accepted.
   *
   * @param overrides the normalised overrides: declared order, empty sections dropped
   * @param complete every declared field, its override or else its deployment default
   * @param effective the integration's effective settings for the connection
   */
  record Resolved(SettingsDocument overrides, SettingsDocument complete, Object effective)
      implements SettingsResolution {

    public Resolved {
      Objects.requireNonNull(overrides, "overrides");
      Objects.requireNonNull(complete, "complete");
      Objects.requireNonNull(effective, "effective");
    }
  }

  /** The settings were rejected, with every violation found. */
  record Rejected(List<SettingsViolation> violations) implements SettingsResolution {

    public Rejected {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("A rejection needs at least one violation");
      }
    }
  }
}
