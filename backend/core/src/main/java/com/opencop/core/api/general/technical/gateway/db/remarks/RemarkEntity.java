// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.remarks;

import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "remarks", schema = "geo")
public class RemarkEntity {
  @Id
  @GeneratedValue
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", updatable = false)
  private UUID userId;

  @Column(name = "source_id", updatable = false)
  private UUID sourceId;

  @Column(name = "object_id", updatable = false)
  private String objectId;

  @Column(name = "content", nullable = false)
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private InternalUserEntity internalUser;

  public UUID getId() {
    return id;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getContent() {
    return content;
  }

  public String getobjectId() {
    return objectId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public InternalUserEntity getInternalUser() {
    return internalUser;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setObjectId(String objectId) {
    this.objectId = objectId;
  }

  public void setSourceId(UUID sourceId) {
    this.sourceId = sourceId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public void update() {
    this.updatedAt = Instant.now();
  }
}
