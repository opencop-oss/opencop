// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import tools.jackson.databind.ObjectMapper;

public class NotificationMapper {

  private final ObjectMapper objectMapper;

  public NotificationMapper(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public NotificationEntity toEntity(OpencopNotification notification) {
    NotificationEntity entity = new NotificationEntity();

    entity.setId(notification.notificationId());
    entity.setCorrelationId(notification.correlationId());
    entity.setIssuerUserId(notification.issuerUserId());
    entity.setTargetUserId(notification.targetUserId());
    entity.setType(notification.content().type().name());
    entity.setSeverity(notification.severity());
    entity.setStatus(
        notification.status() != null ? notification.status() : NotificationStatus.CREATED);
    entity.setCreatedAt(notification.createdAt());
    entity.setReadAt(notification.readAt());
    entity.setContent(objectMapper.writeValueAsString(notification.content()));

    return entity;
  }

  public OpencopNotification toModel(NotificationEntity entity) {
    var content = objectMapper.readValue(entity.getContent(), NotificationContent.class);
    return new OpencopNotification(
        entity.getId(),
        entity.getCorrelationId(),
        entity.getIssuerUserId(),
        entity.getTargetUserId(),
        entity.getSeverity(),
        content,
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.isRead(),
        entity.getReadAt());
  }
}
