// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import com.opencop.core.sdk.model.history.HistoryEvent;
import com.opencop.websocket.ports.business.usecase.history.HistoryEventHandler;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

public class HistoryEventsListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(HistoryEventsListener.class);
  private final ObjectMapper om;
  private final HistoryEventHandler handler;

  public HistoryEventsListener(ObjectMapper om, HistoryEventHandler handler) {
    this.om = om;
    this.handler = handler;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      HistoryEvent event = om.readValue(payload, HistoryEvent.class);
      try {
        handler.handle(event);
      } catch (RuntimeException e) {
        log.error(
            "History delivery handler failed for payload type {} targetUser {} request {} job {}",
            event.type(),
            event.targetUserId(),
            event.requestId(),
            event.jobId(),
            e);
      }
    } catch (Exception e) {
      log.warn("Invalid history pubsub payload: {}", payload, e);
    }
  }
}
