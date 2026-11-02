// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * An integration: the parent of its connection schema versions and of the mappers it owns, and the
 * holder of its display metadata and activation.
 */
@Entity
@Table(name = "integration", schema = "config")
@DynamicUpdate
public class IntegrationIdentityEntity implements Persistable<UUID> {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Transient private boolean isNew = true;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected IntegrationIdentityEntity() {}

  IntegrationIdentityEntity(IntegrationIdentity integration) {
    this.id = integration.id();
    this.name = integration.name();
    this.description = integration.description();
    this.active = integration.active();
  }

  IntegrationIdentity toModel() {
    return new IntegrationIdentity(id, name, description, active);
  }

  void updateDisplayMetadata(String name, String description) {
    this.name = name;
    this.description = description;
  }

  void updateActivation(boolean active) {
    this.active = active;
  }

  @Override
  public UUID getId() {
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
