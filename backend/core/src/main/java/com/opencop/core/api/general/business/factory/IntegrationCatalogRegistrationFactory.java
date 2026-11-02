// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogRegistration;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import java.util.List;
import java.util.Objects;

/** Assembles what the installed integrations publish into one catalog registration. */
public class IntegrationCatalogRegistrationFactory {

  private static final boolean NEW_INTEGRATION_ACTIVE = true;

  /**
   * Keeps every publication, duplicates included, so the catalog policy can reject them. An
   * integration the catalog does not know yet starts active.
   */
  public IntegrationCatalogRegistration create(
      List<Integration> integrations, List<IntegrationConnectionSchema> schemas) {
    Objects.requireNonNull(integrations, "integrations");
    Objects.requireNonNull(schemas, "schemas");
    return new IntegrationCatalogRegistration(
        integrations.stream()
            .map(
                integration ->
                    new IntegrationIdentity(
                        integration.id(),
                        integration.name(),
                        integration.description(),
                        NEW_INTEGRATION_ACTIVE))
            .toList(),
        schemas.stream()
            .map(
                schema ->
                    new IntegrationConnectionSchemaKey(
                        schema.integration().id(), schema.schemaVersion()))
            .toList());
  }
}
