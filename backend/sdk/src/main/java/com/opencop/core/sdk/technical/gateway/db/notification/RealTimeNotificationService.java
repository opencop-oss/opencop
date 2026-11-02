// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RealTimeNotificationService implements NotificationService {

  private static final Logger log = LoggerFactory.getLogger(RealTimeNotificationService.class);
  private final NotificationRepository repo;
  private final NotificationMapper mapper;

  RealTimeNotificationService(NotificationRepository repo, ObjectMapper objectMapper) {
    this.repo = repo;
    this.mapper = new NotificationMapper(objectMapper);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID notificationId) {
    return repo.existsById(notificationId);
  }

  @Transactional
  public void save(OpencopNotification notification) throws PersistenceException {
    try {
      repo.save(mapper.toEntity(notification));
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage());
    }
  }

  @Transactional
  public void markAsRead(UUID notificationId, UUID userId) throws PersistenceException {
    if (repo.existsById(notificationId)) {
      int updated = repo.markAsRead(notificationId, userId);

      if (updated == 0) {
        throw new PersistenceException("Already marked as read: " + notificationId);
      }
    } else {
      throw new PersistenceException(
          "Notification not found or not owned by requestor: " + notificationId);
    }
  }

  @Transactional
  public boolean updateStatus(UUID notificationId, NotificationStatus status) {
    int updated = repo.updateStatus(notificationId, status);

    if (updated == 0) {
      log.error("Notification not found: {}", notificationId);
      return false;
    }
    return true;
  }

  @Transactional(readOnly = true)
  public PageResponse<OpencopNotification> getAll(UUID userId, Pageable pageable) {
    Page<NotificationEntity> page = repo.findByTargetUserIdOrderByCreatedAtDesc(userId, pageable);
    return new PageResponse<>(
        page.getContent().stream().map(mapper::toModel).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public PageResponse<OpencopNotification> getUnread(UUID userId, Pageable pageable) {
    Page<NotificationEntity> page =
        repo.findByTargetUserIdAndReadAtNullOrderByCreatedAtDesc(userId, pageable);
    return new PageResponse<>(
        page.getContent().stream().map(mapper::toModel).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
