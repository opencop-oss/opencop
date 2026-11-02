// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.behavior.ConnectionBehaviorRegistry;
import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import java.util.List;
import java.util.Objects;

/** Creates a real SDK registry for one integration without Spring or a mocking framework. */
public final class ConnectionBehaviorRegistryFixture {

  private ConnectionBehaviorRegistryFixture() {}

  /** Returns a real registry containing the integration and supplied focused contributions. */
  public static ConnectionBehaviorRegistry registryFor(
      IntegrationConnectionSchema schema, ConnectionBehaviorContribution<?>... contributions) {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(contributions, "contributions");
    return new ConnectionBehaviorRegistry(List.of(schema), List.of(contributions));
  }
}
