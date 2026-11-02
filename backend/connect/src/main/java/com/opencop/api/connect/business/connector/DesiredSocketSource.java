// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A socket source as Connect should run it: its desired row together with the integration settings
 * overrides it stores. Only a read that loads the overrides can build one, so empty {@code
 * overrides} always means the connection overrides nothing.
 *
 * @param overrides the overrides the source stores, empty when it overrides nothing
 */
public record DesiredSocketSource(SocketDataSource source, Optional<SettingsOverrides> overrides) {

  public DesiredSocketSource {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(overrides, "overrides");
  }

  public UUID id() {
    return source.id();
  }
}
