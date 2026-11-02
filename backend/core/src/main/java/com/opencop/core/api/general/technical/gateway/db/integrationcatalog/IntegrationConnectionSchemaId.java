// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IntegrationConnectionSchemaId implements Serializable {

  @Column(name = "integration_id", nullable = false, updatable = false)
  private UUID integrationId;

  @Column(name = "schema_version", nullable = false, updatable = false)
  private int schemaVersion;

  protected IntegrationConnectionSchemaId() {}

  private IntegrationConnectionSchemaId(UUID integrationId, int schemaVersion) {
    this.integrationId = integrationId;
    this.schemaVersion = schemaVersion;
  }

  static IntegrationConnectionSchemaId of(IntegrationConnectionSchemaKey schema) {
    return new IntegrationConnectionSchemaId(schema.integrationId(), schema.schemaVersion());
  }

  public UUID getIntegrationId() {
    return integrationId;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof IntegrationConnectionSchemaId that)) {
      return false;
    }
    return schemaVersion == that.schemaVersion && Objects.equals(integrationId, that.integrationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(integrationId, schemaVersion);
  }
}
