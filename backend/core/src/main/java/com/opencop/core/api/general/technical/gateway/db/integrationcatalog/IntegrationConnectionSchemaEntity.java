// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Persistable;

/** One version of the connection contract an integration offers. Rows are never changed. */
@Entity
@Table(name = "integration_connection_schema", schema = "config")
public class IntegrationConnectionSchemaEntity
    implements Persistable<IntegrationConnectionSchemaId> {

  @EmbeddedId private IntegrationConnectionSchemaId id;

  @Transient private boolean isNew = true;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected IntegrationConnectionSchemaEntity() {}

  IntegrationConnectionSchemaEntity(IntegrationConnectionSchemaKey schema) {
    this.id = IntegrationConnectionSchemaId.of(schema);
  }

  IntegrationConnectionSchemaKey toKey() {
    return new IntegrationConnectionSchemaKey(id.getIntegrationId(), id.getSchemaVersion());
  }

  @Override
  public IntegrationConnectionSchemaId getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    isNew = false;
  }
}
