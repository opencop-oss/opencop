// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "runtime_feature", schema = "config")
public class RuntimeFeatureEntity {

  @Id
  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "consumer_class", nullable = false)
  private RuntimeFeatureConsumerClassEntity consumerClass;

  @Enumerated(EnumType.STRING)
  @Column(name = "audience_mode", nullable = false)
  private RuntimeFeatureAudienceModeEntity audienceMode;

  @Column(nullable = false)
  private long revision;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String getName() {
    return name;
  }

  public RuntimeFeatureConsumerClassEntity getConsumerClass() {
    return consumerClass;
  }

  public RuntimeFeatureAudienceModeEntity getAudienceMode() {
    return audienceMode;
  }

  public void setAudienceMode(RuntimeFeatureAudienceModeEntity audienceMode) {
    this.audienceMode = audienceMode;
  }

  public long getRevision() {
    return revision;
  }

  public void setRevision(long revision) {
    this.revision = revision;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
