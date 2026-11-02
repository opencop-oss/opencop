// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaMediaUrlRepository implements MediaUrlRepository {

  private final MediaUrlJpaRepository jpa;
  private final MediaAttachmentJpaRepository attachmentJpa;

  public JpaMediaUrlRepository(
      MediaUrlJpaRepository jpa, MediaAttachmentJpaRepository attachmentJpa) {
    this.jpa = jpa;
    this.attachmentJpa = attachmentJpa;
  }

  @Override
  @Transactional
  public MediaUrlRecord save(MediaUrlRecord record) {
    // media_attachment must be inserted before media_url (FK constraint)
    var attachment = toAttachmentEntity(record);
    attachmentJpa.save(attachment);
    var entity = toEntity(record);
    entity.setAttachment(attachment);
    jpa.save(entity);
    return toRecord(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MediaUrlRecord> findById(UUID id) {
    return jpa.findById(id).map(JpaMediaUrlRepository::toRecord);
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    // media_url (child) deleted first so FK to media_attachment is released,
    // then media_attachment deletion cascades to event_attachment.
    jpa.deleteById(id);
    attachmentJpa.deleteById(id);
  }

  private static MediaAttachmentEntity toAttachmentEntity(MediaUrlRecord r) {
    return MediaAttachmentEntity.of(
        r.id(),
        r.access(),
        r.creatorId(),
        r.ownerId(),
        r.createdAt() != null ? r.createdAt() : Instant.now(),
        r.usage());
  }

  private static MediaUrlEntity toEntity(MediaUrlRecord r) {
    var e = new MediaUrlEntity();
    e.setId(r.id());
    e.setUrl(r.url());
    e.setStreamKind(r.streamKind());
    return e;
  }

  private static MediaUrlRecord toRecord(MediaUrlEntity e) {
    var a = e.getAttachment();
    return new MediaUrlRecord(
        e.getId(),
        e.getUrl(),
        e.getStreamKind(),
        a.getAccess(),
        a.getCreatorId(),
        a.getOwnerId(),
        a.getCreatedAt(),
        a.getUsage());
  }
}
