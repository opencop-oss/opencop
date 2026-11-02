// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.setup;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Objects;
import java.util.Optional;

/**
 * A socket connection's setup once every selection in it is valid and its security material is
 * provisioned, ready to be stored for the connection.
 *
 * @param access the access mode and the mappers the connection reads and writes with
 * @param integration the connection schema version the connection binds to
 * @param spec the connection's spec, referencing the security material provisioned for it
 * @param settings the normalised integration settings overrides, empty when there are none
 * @param prefill the non-secret part of the setup, to fill a later setup form with
 * @param cleanup deletes exactly the security material provisioned for this setup, should storing
 *     it fail; empty when nothing was provisioned
 */
public record ConnectionSetup(
    ConnectionAccess access,
    IntegrationConnectionSchemaReference integration,
    ConnectionSpec spec,
    SettingsDocument settings,
    SetupPrefill prefill,
    Optional<Runnable> cleanup) {

  public ConnectionSetup {
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(integration, "integration");
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(prefill, "prefill");
    Objects.requireNonNull(cleanup, "cleanup");
  }

  /** What the connection's own row stores of this setup. */
  public StoredConnectionSetup stored() {
    return new StoredConnectionSetup(access, integration, spec, prefill);
  }
}
