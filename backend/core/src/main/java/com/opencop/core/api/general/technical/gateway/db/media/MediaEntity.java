// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.technical.gateway.db.media.MediaAttachmentEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "media_file")
public class MediaEntity implements Persistable<UUID> {

  // See MediaAttachmentEntity for why application-assigned IDs need Persistable.
  @Id private UUID id;

  @Transient private boolean isNew = true;

  // Eagerly joined for toRecord() access to shared fields (creator, owner, access, etc.)
  @OneToOne(fetch = FetchType.EAGER)
  @PrimaryKeyJoinColumn
  private MediaAttachmentEntity attachment;

  @Column(name = "blob_key", nullable = false, updatable = false)
  private String blobKey;

  @Column(name = "original_filename", nullable = false, updatable = false)
  private String originalFilename;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "content_type",
      nullable = false,
      updatable = false,
      columnDefinition = "media_content_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private MediaContentType contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "mime_type")
  private String mimeType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private MediaAttributes attributes;

  @Column(name = "thumbnail_of")
  private UUID thumbnailOf;

  public MediaEntity() {}

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

  public MediaAttachmentEntity getAttachment() {
    return attachment;
  }

  public void setAttachment(MediaAttachmentEntity attachment) {
    this.attachment = attachment;
  }

  public String getBlobKey() {
    return blobKey;
  }

  public void setBlobKey(String blobKey) {
    this.blobKey = blobKey;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  public MediaContentType getContentType() {
    return contentType;
  }

  public void setContentType(MediaContentType contentType) {
    this.contentType = contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public MediaAttributes getAttributes() {
    return attributes;
  }

  public void setAttributes(MediaAttributes attributes) {
    this.attributes = attributes;
  }

  public UUID getThumbnailOf() {
    return thumbnailOf;
  }

  public void setThumbnailOf(UUID thumbnailOf) {
    this.thumbnailOf = thumbnailOf;
  }
}
