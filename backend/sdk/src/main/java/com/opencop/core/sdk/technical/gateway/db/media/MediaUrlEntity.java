// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import com.opencop.core.sdk.model.media.StreamKind;
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
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "media_url")
public class MediaUrlEntity implements Persistable<UUID> {

  @Id private UUID id;

  @Transient private boolean isNew = true;

  // Eagerly joined for toRecord() access to shared fields (creator, owner, access, etc.)
  @OneToOne(fetch = FetchType.EAGER)
  @PrimaryKeyJoinColumn
  private MediaAttachmentEntity attachment;

  @Column(name = "url", nullable = false, updatable = false)
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(name = "stream_kind", nullable = false, updatable = false)
  private StreamKind streamKind;

  public MediaUrlEntity() {}

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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public StreamKind getStreamKind() {
    return streamKind;
  }

  public void setStreamKind(StreamKind streamKind) {
    this.streamKind = streamKind;
  }
}
