// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.notification;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.websocket.ports.business.usecase.notification.NotificationHandler;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class UserNotificationHandler implements NotificationHandler {
  private final SocketProducer producer;
  private final ObjectMapper mapper;

  public UserNotificationHandler(final SocketProducer producer) {
    this.producer = producer;
    mapper = JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
  }

  @Override
  public void notify(UUID userId, OpencopNotification opencopNotification) {
    try {
      var bytes = mapper.writeValueAsBytes(opencopNotification);
      this.producer.sendToUser(userId, "/user/queue/notification", bytes, false);
    } catch (JacksonException _) {
      throw new GlobalOpenCopException(
          "Failed to parse notification id: %s".formatted(opencopNotification.notificationId()),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
