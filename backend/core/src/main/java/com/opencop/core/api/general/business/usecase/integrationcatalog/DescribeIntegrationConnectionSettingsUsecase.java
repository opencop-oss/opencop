// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupRejection;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSetupUnavailableException;
import com.opencop.core.api.general.business.policy.IntegrationConnectionSetupPolicy;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSettings;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import java.util.Objects;
import java.util.UUID;

/**
 * Describes an integration schema version's connection settings, answering unknown, inactive and
 * unavailable references exactly as connection setup does.
 */
public class DescribeIntegrationConnectionSettingsUsecase
    implements DescribeIntegrationConnectionSettings {

  private final IntegrationCatalogGateway catalogGateway;
  private final ConnectionSettingsRegistry settingsRegistry;

  public DescribeIntegrationConnectionSettingsUsecase(
      IntegrationCatalogGateway catalogGateway, ConnectionSettingsRegistry settingsRegistry) {
    this.catalogGateway = Objects.requireNonNull(catalogGateway, "catalogGateway");
    this.settingsRegistry = Objects.requireNonNull(settingsRegistry, "settingsRegistry");
  }

  /**
   * The gateway read runs in its own transaction, so this method deliberately declares none, as
   * connection setup does.
   */
  @Override
  public ConnectionSettingsDescriptor describe(UUID integrationId, int schemaVersion) {
    if (integrationId == null || schemaVersion <= 0) {
      throw new IntegrationConnectionSetupUnavailableException(
          IntegrationConnectionSetupRejection.UNKNOWN);
    }
    var key = new IntegrationConnectionSchemaKey(integrationId, schemaVersion);
    var registeredSchema =
        settingsRegistry.find(
            new IntegrationConnectionSchemaReference(integrationId, schemaVersion));
    IntegrationConnectionSetupPolicy.unavailability(
            catalogGateway.findSchemaOwner(key), registeredSchema)
        .ifPresent(
            reason -> {
              throw new IntegrationConnectionSetupUnavailableException(reason);
            });
    return settingsRegistry.descriptor(registeredSchema.orElseThrow());
  }
}
