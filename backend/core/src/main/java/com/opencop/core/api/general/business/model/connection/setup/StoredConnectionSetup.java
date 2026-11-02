// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.setup;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Objects;

/**
 * What a connection's own row stores of its setup. Its integration settings live apart from the
 * row, and its cleanup only matters until it is stored.
 *
 * @param access the access mode and the mappers the connection reads and writes with
 * @param integration the connection schema version the connection binds to
 * @param spec the connection's spec, referencing the security material provisioned for it
 * @param prefill the non-secret part of the setup, to fill a later setup form with
 */
public record StoredConnectionSetup(
    ConnectionAccess access,
    IntegrationConnectionSchemaReference integration,
    ConnectionSpec spec,
    SetupPrefill prefill) {

  public StoredConnectionSetup {
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(integration, "integration");
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(prefill, "prefill");
  }
}
