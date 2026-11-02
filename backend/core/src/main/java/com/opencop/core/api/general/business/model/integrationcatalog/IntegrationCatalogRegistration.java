// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.List;

/**
 * What the installed integrations publish to the catalog: each integration, and each version of a
 * connection contract one of them offers.
 */
public record IntegrationCatalogRegistration(
    List<IntegrationIdentity> integrations, List<IntegrationConnectionSchemaKey> schemas) {

  public IntegrationCatalogRegistration {
    integrations = List.copyOf(integrations);
    schemas = List.copyOf(schemas);
  }
}
