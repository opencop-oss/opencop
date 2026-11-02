// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.processor.ports.business.usecase.notification.NotificationHandler;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationConsumer {

  private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

  private final NotificationHandler<OpencopNotification> usecase;
  private final ObjectMapper objectMapper;

  NotificationConsumer(final NotificationHandler<OpencopNotification> usecase) {
    this.usecase = usecase;
    objectMapper = new ObjectMapper();
  }

  @KafkaListener(
      id = "notificationConsumer",
      topics = "${opencop.messaging-broker.listeners.notification.topic}",
      containerFactory = "notificationFactory")
  public void notifyUser(byte[] opencopNotification) {
    int payloadBytes = opencopNotification == null ? 0 : opencopNotification.length;
    log.info("Consuming notification payload: bytes={}", payloadBytes);

    try {
      var notification = objectMapper.readValue(opencopNotification, OpencopNotification.class);
      if (log.isDebugEnabled()) {
        log.debug(
            "Notification payload details: notificationId={}, correlationId={}, issuerUserId={}, targetUserId={}, severity={}",
            safeUuid(notification.notificationId()),
            safeUuid(notification.correlationId()),
            safeUuid(notification.issuerUserId()),
            safeUuid(notification.targetUserId()),
            notification.severity());
      }

      usecase.notify(notification);
      log.info(
          "Notification processed successfully: notificationId={}, targetUserId={}, severity={}",
          safeUuid(notification.notificationId()),
          safeUuid(notification.targetUserId()),
          notification.severity());
    } catch (Exception e) {
      log.error("Notification processing failed: bytes={}", payloadBytes, e);
      throw e;
    }
  }

  private static String safeUuid(UUID value) {
    return value == null ? "null" : value.toString();
  }
}
