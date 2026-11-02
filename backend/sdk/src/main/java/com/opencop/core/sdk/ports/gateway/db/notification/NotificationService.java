// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.db.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

  boolean exists(UUID notificationId);

  void save(OpencopNotification notification) throws PersistenceException;

  void markAsRead(UUID notificationId, UUID userId) throws PersistenceException;

  boolean updateStatus(UUID notificationId, NotificationStatus status);

  PageResponse<OpencopNotification> getAll(UUID userId, Pageable pageable);

  PageResponse<OpencopNotification> getUnread(UUID userId, Pageable pageable);
}
