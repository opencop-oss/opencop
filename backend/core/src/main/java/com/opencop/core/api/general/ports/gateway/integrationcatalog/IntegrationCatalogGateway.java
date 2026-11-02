// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogSnapshot;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationCatalogGateway {

  /**
   * Everything the catalog records, in no particular order. Callers that present the result are
   * responsible for ordering it. Every schema version returned carries the integration that owns
   * it.
   */
  IntegrationCatalogSnapshot findAll();

  Optional<IntegrationIdentity> findIntegration(UUID integrationId);

  /** Locks the integration against concurrent activation changes until the transaction ends. */
  Optional<IntegrationIdentity> findIntegrationForUpdate(UUID integrationId);

  boolean hasSchema(IntegrationConnectionSchemaKey schema);

  /** The integration offering this connection schema version; empty when the version is unknown. */
  Optional<IntegrationIdentity> findSchemaOwner(IntegrationConnectionSchemaKey schema);

  /**
   * Like {@link #findSchemaOwner}, and locks the owning integration against concurrent activation
   * changes until the transaction ends.
   */
  Optional<IntegrationIdentity> findSchemaOwnerForUpdate(IntegrationConnectionSchemaKey schema);

  void insertIntegration(IntegrationIdentity integration);

  void insertSchema(IntegrationConnectionSchemaKey schema);

  void updateDisplayMetadata(IntegrationIdentity integration);

  void updateActivation(UUID integrationId, boolean active);
}
