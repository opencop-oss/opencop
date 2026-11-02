// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.List;

/**
 * Everything the catalog records at one moment: every integration, and every version of a
 * connection contract one of them offers.
 *
 * <p>The two lists are read together because a schema version is meaningless without the
 * integration that owns its display metadata and activation. Every version here has its
 * integration: a version only exists under a foreign key to one, production never deletes either,
 * and the gateway reads the versions first so the integrations it then reads can only be a
 * superset.
 */
public record IntegrationCatalogSnapshot(
    List<IntegrationIdentity> integrations, List<IntegrationConnectionSchemaKey> schemas) {

  public IntegrationCatalogSnapshot {
    integrations = List.copyOf(integrations);
    schemas = List.copyOf(schemas);
  }
}
