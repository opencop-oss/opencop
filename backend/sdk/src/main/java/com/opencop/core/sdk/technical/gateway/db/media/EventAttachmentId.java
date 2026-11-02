// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class EventAttachmentId implements Serializable {

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @Column(name = "object_id", nullable = false)
  private String objectId;

  @Column(name = "attachment_id", nullable = false)
  private UUID attachmentId;

  public EventAttachmentId() {}

  public EventAttachmentId(UUID sourceId, String objectId, UUID attachmentId) {
    this.sourceId = sourceId;
    this.objectId = objectId;
    this.attachmentId = attachmentId;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getObjectId() {
    return objectId;
  }

  public UUID getAttachmentId() {
    return attachmentId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EventAttachmentId that)) return false;
    return Objects.equals(sourceId, that.sourceId)
        && Objects.equals(objectId, that.objectId)
        && Objects.equals(attachmentId, that.attachmentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sourceId, objectId, attachmentId);
  }
}
