// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.notification;

import com.opencop.core.sdk.model.notification.NotificationCommand;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
  private final MessageBrokerProducer<OpencopNotification> messageBrokerProducer;

  public NotificationWorker(
      final MessageBrokerProducer<OpencopNotification> messageBrokerProducer) {
    this.messageBrokerProducer = messageBrokerProducer;
  }

  @Async
  public void sendAsync(NotificationCommand command) {
    this.send(command);
  }

  @Async
  public void sendAllAsync(Collection<NotificationCommand> commands) {
    for (NotificationCommand command : commands) {
      this.send(command);
    }
  }

  private void send(NotificationCommand command) {
    try {
      var notification = buildNotification(command);
      this.messageBrokerProducer.send(notification);
    } catch (Exception e) {
      log.error(
          "Failed to send notification to {} (type={})",
          command.recipientUserId(),
          command.type(),
          e);
    }
  }

  private OpencopNotification buildNotification(NotificationCommand command) {
    return new OpencopNotification(
        UUID.randomUUID(),
        command.correlationId(),
        command.issuerUserId(),
        command.recipientUserId(),
        command.severity(),
        command.content(),
        NotificationStatus.CREATED,
        Instant.now(),
        false,
        null);
  }
}
