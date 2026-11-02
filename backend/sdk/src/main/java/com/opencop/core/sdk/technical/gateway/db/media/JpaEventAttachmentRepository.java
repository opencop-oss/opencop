// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import com.opencop.core.sdk.ports.gateway.media.EventAttachmentAssociation;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaEventAttachmentRepository implements EventAttachmentRepository {

  private final EventAttachmentJpaRepository jpa;

  public JpaEventAttachmentRepository(EventAttachmentJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID sourceId, String objectId, UUID attachmentId) {
    return jpa.existsByIdSourceIdAndIdObjectIdAndIdAttachmentId(sourceId, objectId, attachmentId);
  }

  @Override
  @Transactional
  public void save(UUID sourceId, String objectId, UUID attachmentId) {
    jpa.save(new EventAttachmentEntity(new EventAttachmentId(sourceId, objectId, attachmentId)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findAttachmentIdsByObjectId(UUID sourceId, String objectId) {
    return jpa.findByIdSourceIdAndIdObjectId(sourceId, objectId).stream()
        .map(e -> e.getId().getAttachmentId())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<EventAttachmentAssociation> findAssociationsByAttachmentId(UUID attachmentId) {
    return jpa.findByIdAttachmentId(attachmentId).stream()
        .map(
            eventAttachment ->
                new EventAttachmentAssociation(
                    eventAttachment.getId().getSourceId(),
                    eventAttachment.getId().getObjectId(),
                    eventAttachment.getId().getAttachmentId()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public int countAttachmentsByObjectId(UUID sourceId, String objectId) {
    return jpa.countByIdSourceIdAndIdObjectId(sourceId, objectId);
  }

  @Override
  @Transactional
  public void delete(UUID sourceId, String objectId, UUID attachmentId) {
    jpa.deleteByIdSourceIdAndIdObjectIdAndIdAttachmentId(sourceId, objectId, attachmentId);
  }
}
