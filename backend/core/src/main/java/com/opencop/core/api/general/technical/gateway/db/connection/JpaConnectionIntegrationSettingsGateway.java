// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaConnectionIntegrationSettingsGateway
    implements ConnectionIntegrationSettingsGateway {

  private final ConnectionIntegrationSettingsJpaRepository repository;

  JpaConnectionIntegrationSettingsGateway(ConnectionIntegrationSettingsJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(
      UUID sourceId,
      IntegrationConnectionSchemaReference integration,
      SettingsOverrides overrides) {
    repository.save(
        new ConnectionIntegrationSettingsEntity(
            sourceId,
            integration.integrationId(),
            integration.schemaVersion(),
            overrides.sections()));
  }

  @Override
  public void delete(UUID sourceId) {
    // Flushed so that later statements of the transaction no longer see the row, while the
    // entity is removed from the persistence context rather than left stale in it.
    repository
        .findById(sourceId)
        .ifPresent(
            settings -> {
              repository.delete(settings);
              repository.flush();
            });
  }

  @Override
  public Optional<SettingsOverrides> find(UUID sourceId) {
    return repository.findById(sourceId).map(JpaConnectionIntegrationSettingsGateway::overrides);
  }

  @Override
  public Map<UUID, SettingsOverrides> findAll(Collection<UUID> sourceIds) {
    var found = new HashMap<UUID, SettingsOverrides>();
    repository
        .findAllById(sourceIds)
        .forEach(entity -> found.put(entity.sourceId(), overrides(entity)));
    return found;
  }

  private static SettingsOverrides overrides(ConnectionIntegrationSettingsEntity entity) {
    return new SettingsOverrides(entity.settings());
  }
}
