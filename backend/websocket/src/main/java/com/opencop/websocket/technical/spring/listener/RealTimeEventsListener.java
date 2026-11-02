// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import com.opencop.core.sdk.model.cache.CacheEventsNotification;
import com.opencop.websocket.ports.business.usecase.event.EventHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

public class RealTimeEventsListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RealTimeEventsListener.class);
  private final ObjectMapper om;
  private final EventHandler<List<String>> geoHandler;

  public RealTimeEventsListener(ObjectMapper om, EventHandler<List<String>> geoHandler) {
    this.om = om;
    this.geoHandler = geoHandler;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      CacheEventsNotification event = om.readValue(payload, CacheEventsNotification.class);
      this.geoHandler.handle(
          event.sourceId().toString(),
          event.objectIds(),
          "/topic/queue/event/%s".formatted(event.sourceId()));
    } catch (Exception e) {
      // don’t crash the listener thread
      log.warn("Invalid pubsub payload: {}", payload, e);
    }
  }
}
