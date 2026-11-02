// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_attachment")
public class EventAttachmentEntity {

  @EmbeddedId private EventAttachmentId id;

  public EventAttachmentEntity() {}

  public EventAttachmentEntity(EventAttachmentId id) {
    this.id = id;
  }

  public EventAttachmentId getId() {
    return id;
  }

  public void setId(EventAttachmentId id) {
    this.id = id;
  }
}
