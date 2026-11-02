// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAttachmentJpaRepository
    extends JpaRepository<EventAttachmentEntity, EventAttachmentId> {

  boolean existsByIdSourceIdAndIdObjectIdAndIdAttachmentId(
      UUID sourceId, String objectId, UUID attachmentId);

  List<EventAttachmentEntity> findByIdSourceIdAndIdObjectId(UUID sourceId, String objectId);

  List<EventAttachmentEntity> findByIdAttachmentId(UUID attachmentId);

  int countByIdSourceIdAndIdObjectId(UUID sourceId, String objectId);

  void deleteByIdSourceIdAndIdObjectIdAndIdAttachmentId(
      UUID sourceId, String objectId, UUID attachmentId);
}
