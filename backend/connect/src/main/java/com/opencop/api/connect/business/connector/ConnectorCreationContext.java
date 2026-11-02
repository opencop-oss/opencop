// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Complete source and connection context required to create a socket connector.
 *
 * @param integration the connection schema version the source binds to, or null for a raw connector
 * @param overrides the integration settings overrides the source stores, empty when it overrides
 *     none
 */
public record ConnectorCreationContext(
    UUID sourceId,
    ConnectionSpec spec,
    IntegrationConnectionSchemaReference integration,
    Optional<SettingsOverrides> overrides) {

  public ConnectorCreationContext {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(overrides, "overrides");
  }
}
