// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * The Native integration as Core's integration catalog registers it at startup, for tests that
 * store connections in a database Core has not started on. A stored connection binds to an
 * integration's connection schema version, so a fixture seeds Native before writing {@link
 * #CONNECTION_SCHEMA} into the connection's {@code integration_id} and {@code
 * connection_schema_version}.
 */
public final class NativeIntegrationFixture {

  /** The integration and connection schema version a fixture's connection binds to. */
  public static final IntegrationConnectionSchemaReference CONNECTION_SCHEMA =
      NativeConnectionSchema.V1_REFERENCE;

  private static final NativeIntegration INTEGRATION = new NativeIntegration();

  private NativeIntegrationFixture() {}

  /**
   * Registers Native and its connection schema version where they are missing, and reactivates
   * Native if a test deactivated it, since an operator never can.
   */
  public static void seed(JdbcOperations jdbc) {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description)
        VALUES (?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET active = true
        """,
        INTEGRATION.id(),
        INTEGRATION.name(),
        INTEGRATION.description());
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (?, ?)
        ON CONFLICT (integration_id, schema_version) DO NOTHING
        """,
        CONNECTION_SCHEMA.integrationId(),
        CONNECTION_SCHEMA.schemaVersion());
  }
}
