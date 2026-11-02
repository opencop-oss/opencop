// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.iam.access;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerLifecycleState;
import java.time.Instant;
import java.util.UUID;

public class Layer {

  private UUID id;
  private String name;
  private Instant createdAt;
  private UUID createdBy;
  private Instant updatedAt;
  private UUID updatedBy;
  private Instant lastEventAt;
  private Instant archivedAt;
  private UUID archivedBy;
  private LayerAccessScope accessScope;
  private LayerLifecycleState lifecycleState = LayerLifecycleState.ACTIVE;
  private boolean defaultVisible = true;

  public Layer() {}

  private Layer(Builder builder) {
    id = builder.id;
    name = builder.name;
    createdAt = builder.createdAt;
    createdBy = builder.createdBy;
    updatedAt = builder.updatedAt;
    updatedBy = builder.updatedBy;
    lastEventAt = builder.lastEventAt;
    archivedAt = builder.archivedAt;
    archivedBy = builder.archivedBy;
    accessScope = builder.accessScope;
    lifecycleState = builder.lifecycleState;
    defaultVisible = builder.defaultVisible;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UUID updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Instant getLastEventAt() {
    return lastEventAt;
  }

  public void setLastEventAt(Instant lastEventAt) {
    this.lastEventAt = lastEventAt;
  }

  public LayerAccessScope getAccessScope() {
    return accessScope;
  }

  public void setAccessScope(LayerAccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public LayerLifecycleState getLifecycleState() {
    return lifecycleState;
  }

  public void setLifecycleState(LayerLifecycleState lifecycleState) {
    this.lifecycleState = lifecycleState;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant archivedAt) {
    this.archivedAt = archivedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(UUID archivedBy) {
    this.archivedBy = archivedBy;
  }

  public boolean isDefaultVisible() {
    return defaultVisible;
  }

  public void setDefaultVisible(boolean defaultVisible) {
    this.defaultVisible = defaultVisible;
  }

  public static class Builder {
    private UUID id;
    private String name;
    private Instant createdAt;
    private UUID createdBy;
    private Instant updatedAt;
    private UUID updatedBy;
    private Instant lastEventAt;
    private Instant archivedAt;
    private UUID archivedBy;
    private LayerAccessScope accessScope;
    private LayerLifecycleState lifecycleState = LayerLifecycleState.ACTIVE;
    private boolean defaultVisible = true;

    public Builder withId(UUID id) {
      this.id = id;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withCreatedBy(UUID createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Builder withUpdatedBy(UUID updatedBy) {
      this.updatedBy = updatedBy;
      return this;
    }

    public Builder withLastEventAt(Instant lastEventAt) {
      this.lastEventAt = lastEventAt;
      return this;
    }

    public Builder withArchivedAt(Instant archivedAt) {
      this.archivedAt = archivedAt;
      return this;
    }

    public Builder withArchivedBy(UUID archivedBy) {
      this.archivedBy = archivedBy;
      return this;
    }

    public Builder withAccessScope(LayerAccessScope accessScope) {
      this.accessScope = accessScope;
      return this;
    }

    public Builder withLifecycleState(LayerLifecycleState lifecycleState) {
      this.lifecycleState = lifecycleState;
      return this;
    }

    public Builder withDefaultVisible(boolean defaultVisible) {
      this.defaultVisible = defaultVisible;
      return this;
    }

    public Layer build() {
      return new Layer(this);
    }
  }
}
