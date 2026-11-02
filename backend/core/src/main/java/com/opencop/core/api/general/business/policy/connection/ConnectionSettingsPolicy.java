// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.List;

/** What a connection's requested integration settings overrides must satisfy to be stored. */
public final class ConnectionSettingsPolicy {

  private ConnectionSettingsPolicy() {}

  /**
   * The normalised overrides, once they resolve against the integration's declared settings. Every
   * violation is reported together.
   */
  public static SettingsDocument requireResolved(SettingsResolution resolution) {
    return switch (resolution) {
      case SettingsResolution.Resolved resolved -> resolved.overrides();
      case SettingsResolution.Rejected rejected ->
          throw ConnectionManagementException.settingsRejected(
              ConnectionCreationDetails.SETTINGS_FIELD, rejected.violations());
    };
  }

  /** Kafka connections run no integration behavior, so they have nothing to apply settings to. */
  public static void requireNone(SettingsDocument settings) {
    if (!settings.isEmpty()) {
      throw ConnectionManagementException.settingsRejected(
          ConnectionCreationDetails.SETTINGS_FIELD, List.of(SettingsViolation.notSupported()));
    }
  }

  /**
   * Rejects overrides the connection's transport cannot apply: a Kafka connection runs no
   * integration behavior, whatever its integration declares.
   */
  public static void assertTransportAccepts(TransportKind transport, SettingsDocument settings) {
    if (transport == TransportKind.KAFKA) {
      requireNone(settings);
    }
  }
}
