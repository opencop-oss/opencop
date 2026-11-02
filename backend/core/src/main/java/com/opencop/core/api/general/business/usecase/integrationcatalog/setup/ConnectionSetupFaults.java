// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;

/**
 * The fault a describer raises when the connection registries admitted a wiring their construction
 * must reject. It is a server fault rather than an answer to the client.
 */
public final class ConnectionSetupFaults {

  private ConnectionSetupFaults() {}

  public static IllegalStateException registeredWithout(
      String missing, IntegrationConnectionSchemaReference schema) {
    return new IllegalStateException(
        "Connection schema " + schema + " is registered without " + missing);
  }
}
