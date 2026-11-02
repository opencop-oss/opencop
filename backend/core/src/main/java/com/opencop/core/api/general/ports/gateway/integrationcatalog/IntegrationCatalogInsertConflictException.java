// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import java.util.UUID;

/** Another synchronizer inserted the same catalog entry first. */
public class IntegrationCatalogInsertConflictException extends RuntimeException {

  private IntegrationCatalogInsertConflictException(String message, Throwable cause) {
    super(message, cause);
  }

  public static IntegrationCatalogInsertConflictException forIntegration(
      UUID integrationId, Throwable cause) {
    return new IntegrationCatalogInsertConflictException(
        "Integration insert conflict for " + integrationId, cause);
  }

  public static IntegrationCatalogInsertConflictException forSchema(
      IntegrationConnectionSchemaKey schema, Throwable cause) {
    return new IntegrationCatalogInsertConflictException(
        "Connection schema insert conflict for " + schema, cause);
  }
}
