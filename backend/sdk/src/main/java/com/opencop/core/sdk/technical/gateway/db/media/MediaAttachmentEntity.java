// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "media_attachment")
public class MediaAttachmentEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Transient private boolean isNew = true;

  @Enumerated(EnumType.STRING)
  @Column(name = "access", nullable = false, columnDefinition = "media_access")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private MediaAccess access;

  @Column(name = "creator_id", updatable = false)
  private UUID creatorId;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "usage", columnDefinition = "media_usage")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private MediaUsage usage;

  public MediaAttachmentEntity() {}

  public static MediaAttachmentEntity of(
      UUID id,
      MediaAccess access,
      UUID creatorId,
      UUID ownerId,
      Instant createdAt,
      MediaUsage usage) {
    var a = new MediaAttachmentEntity();
    a.setId(id);
    a.setAccess(access);
    a.setCreatorId(creatorId);
    a.setOwnerId(ownerId);
    a.setCreatedAt(createdAt);
    a.setUsage(usage);
    return a;
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
    this.isNew = false;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public MediaAccess getAccess() {
    return access;
  }

  public void setAccess(MediaAccess access) {
    this.access = access;
  }

  public UUID getCreatorId() {
    return creatorId;
  }

  public void setCreatorId(UUID creatorId) {
    this.creatorId = creatorId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public MediaUsage getUsage() {
    return usage;
  }

  public void setUsage(MediaUsage usage) {
    this.usage = usage;
  }
}
