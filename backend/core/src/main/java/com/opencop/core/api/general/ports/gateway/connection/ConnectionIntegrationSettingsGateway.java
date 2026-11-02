// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the integration settings overrides of connections. A connection without stored overrides
 * overrides nothing. Stored overrides are read back as stored, for the caller to resolve again.
 */
public interface ConnectionIntegrationSettingsGateway {

  /**
   * Stores a connection's normalised overrides for the integration schema version it selects. Joins
   * the caller's transaction.
   */
  void save(
      UUID sourceId, IntegrationConnectionSchemaReference integration, SettingsOverrides overrides);

  /**
   * Deletes a connection's overrides, so it overrides nothing. Joins the caller's transaction and
   * takes effect within it at once, before the statements the transaction runs next.
   */
  void delete(UUID sourceId);

  Optional<SettingsOverrides> find(UUID sourceId);

  /** Returns the overrides of every listed connection that has any. */
  Map<UUID, SettingsOverrides> findAll(Collection<UUID> sourceIds);
}
