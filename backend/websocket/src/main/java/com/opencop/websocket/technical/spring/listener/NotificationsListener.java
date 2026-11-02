// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.websocket.ports.business.usecase.notification.NotificationHandler;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

public class NotificationsListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationsListener.class);
  private final ObjectMapper om;
  private final NotificationHandler handler;

  public NotificationsListener(ObjectMapper om, NotificationHandler handler) {
    this.om = om;
    this.handler = handler;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      OpencopNotification notification = om.readValue(payload, OpencopNotification.class);

      this.handler.notify(notification.targetUserId(), notification);
    } catch (Exception e) {
      // don’t crash the listener thread
      log.warn("Invalid pubsub payload: {}", payload, e);
    }
  }
}
