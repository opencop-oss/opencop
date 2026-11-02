// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.ports.gateway.cache.CachePublisher;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.processor.ports.business.usecase.event.OpencopBrokerConsumerException;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import org.jspecify.annotations.NonNull;

public class UserNotificationHandler implements NotificationHandler<OpencopNotification> {

  private final CachePublisher<OpencopNotification> cachePublisher;
  private final NotificationService notificationService;

  public UserNotificationHandler(
      final CachePublisher<OpencopNotification> cachePublisher,
      final NotificationService notificationService) {
    this.cachePublisher = cachePublisher;
    this.notificationService = notificationService;
  }

  @Override
  public void notify(@NonNull OpencopNotification notification) {
    try {
      if (!notificationService.exists(notification.notificationId())) {
        notificationService.save(notification);
      }
      notificationService.updateStatus(notification.notificationId(), NotificationStatus.PENDING);
      this.cachePublisher.publish(notification);
      notificationService.updateStatus(notification.notificationId(), NotificationStatus.SENT);
    } catch (PersistenceException e) {
      throw new OpencopBrokerConsumerException(
          "Failed to persist notification: %s | cause: %s"
              .formatted(notification.notificationId(), e.getMessage()),
          e);
    }
  }
}
