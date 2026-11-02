// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.notification;

import com.opencop.core.api.general.ports.gateway.notification.NotificationGateway;
import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.NotificationType;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.DataSourceAccessNotification;
import com.opencop.core.sdk.model.notification.content.NotificationContent;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sends notifications through the asynchronous {@link NotificationWorker}. Each operation names the
 * fields of its kind of notification and sends it the same way.
 *
 * <p>A separate adapter rather than the worker implementing the port: the worker is proxied for
 * {@code @Async}, and an interface on it would turn that proxy into one of the interface, which the
 * beans injecting the worker by class could no longer receive.
 */
@Component
public class AsyncNotificationGateway implements NotificationGateway {

  // Each operation tells one recipient, so there is no batch to correlate.
  private static final UUID NO_CORRELATION = null;

  private final NotificationWorker worker;

  AsyncNotificationGateway(NotificationWorker worker) {
    this.worker = worker;
  }

  @Override
  public void notifyAddedOwner(UUID sourceId, UUID ownerId) {
    send(
        ownerId,
        ownerId,
        NotificationType.SOURCE_ACCESS,
        NotificationSeverityType.NONE,
        new DataSourceAccessNotification(
            sourceId, AccessOperationType.ADDED, ServerRole.OWNER.name()));
  }

  private void send(
      UUID issuerId,
      UUID recipientId,
      NotificationType type,
      NotificationSeverityType severity,
      NotificationContent content) {
    worker.sendAsync(
        new NotificationCommand(NO_CORRELATION, issuerId, recipientId, type, severity, content));
  }
}
