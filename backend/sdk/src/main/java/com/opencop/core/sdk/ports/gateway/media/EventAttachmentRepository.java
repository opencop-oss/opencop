// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.media;

import java.util.List;
import java.util.UUID;

public interface EventAttachmentRepository {

  boolean exists(UUID sourceId, String objectId, UUID attachmentId);

  void save(UUID sourceId, String objectId, UUID attachmentId);

  List<UUID> findAttachmentIdsByObjectId(UUID sourceId, String objectId);

  List<EventAttachmentAssociation> findAssociationsByAttachmentId(UUID attachmentId);

  int countAttachmentsByObjectId(UUID sourceId, String objectId);

  void delete(UUID sourceId, String objectId, UUID attachmentId);
}
