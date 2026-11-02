// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable connection schema of a test integration, for plugin-owner registry tests. */
public record TestConnectionSchema(
    UUID integrationId,
    int schemaVersion,
    String name,
    String description,
    Set<TransportProtocol> protocols)
    implements IntegrationConnectionSchema {

  public TestConnectionSchema {
    Objects.requireNonNull(integrationId, "integrationId");
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    name = requireText(name, "name");
    description = requireText(description, "description");
    protocols = Set.copyOf(Objects.requireNonNull(protocols, "protocols"));
  }

  /** A schema declaring no transport protocol, valid when it registers no connection extractor. */
  public TestConnectionSchema(
      UUID integrationId, int schemaVersion, String name, String description) {
    this(integrationId, schemaVersion, name, description, Set.of());
  }

  @Override
  public Integration integration() {
    return new TestIntegration(integrationId, name, description);
  }

  /** The integration offering a {@link TestConnectionSchema}. */
  public record TestIntegration(UUID id, String name, String description) implements Integration {}

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
