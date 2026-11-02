// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.integrationcatalog;

import java.util.Objects;
import java.util.UUID;

/**
 * One version of a connection contract as connection setup sees it: the catalog's record of the
 * integration offering it, plus whether a loaded plugin provides it in the runtime answering the
 * request.
 *
 * <p>A connection binds to one exact schema version, so each entry is one selectable choice.
 * Display metadata belongs to the integration rather than the version, so it repeats across the
 * versions of one integration.
 *
 * @param available whether a loaded plugin provides this version in the runtime answering the
 *     request. Computed per request and never persisted, so the same catalog row may be available
 *     on one node and not another. It means only that the version is provided, not that connection
 *     setup can be described for it.
 */
public record IntegrationCatalogEntry(
    UUID integrationId, int schemaVersion, String name, String description, boolean available) {

  public IntegrationCatalogEntry {
    Objects.requireNonNull(integrationId, "integrationId");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
  }

  public static IntegrationCatalogEntry of(
      IntegrationIdentity integration, int schemaVersion, boolean available) {
    Objects.requireNonNull(integration, "integration");
    return new IntegrationCatalogEntry(
        integration.id(), schemaVersion, integration.name(), integration.description(), available);
  }

  public IntegrationConnectionSchemaKey key() {
    return new IntegrationConnectionSchemaKey(integrationId, schemaVersion);
  }
}
