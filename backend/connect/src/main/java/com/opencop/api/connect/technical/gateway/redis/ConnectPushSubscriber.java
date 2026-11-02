// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.redis;

import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.core.sdk.model.push.PushMessage;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

public class ConnectPushSubscriber implements MessageListener {
  private static final Logger log = LoggerFactory.getLogger(ConnectPushSubscriber.class);

  private final ConnectionManager connectionManager;
  private final ObjectMapper objectMapper;

  public ConnectPushSubscriber(ConnectionManager connectionManager, ObjectMapper objectMapper) {
    this.connectionManager = connectionManager;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onMessage(Message message, byte @Nullable [] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);

    try {
      PushMessage pushMessage = objectMapper.readValue(payload, PushMessage.class);
      log.info(
          "Attempted to send {} payloads to {}",
          pushMessage.payload().size(),
          pushMessage.sourceId());

      for (int i = 0; i < pushMessage.payload().size(); i++) {
        var p = pushMessage.payload().get(i);
        try {
          connectionManager.send(pushMessage.sourceId(), p);
        } catch (Exception e) {
          log.warn("Failed to send payload {} to source {}", i, pushMessage.sourceId());
        }
      }
    } catch (Exception e) {
      log.warn("Invalid command payload: {}", payload, e);
    }
  }
}
