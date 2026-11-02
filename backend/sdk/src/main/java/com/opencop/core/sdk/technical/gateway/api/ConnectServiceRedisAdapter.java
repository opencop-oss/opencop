// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.push.PushMessage;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import com.opencop.core.sdk.ports.gateway.api.ConnectServiceException;
import com.opencop.core.sdk.ports.gateway.api.ConnectServiceExceptionCodes;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes PUSH payloads to Connect instances via Redis pub/sub.
 *
 * <p>PUSH is a payload delivery path, not control-plane authority. START/STOP desired-state nudges
 * are handled through Postgres notifications and Connect reconciliation.
 *
 * <p>Throws {@link ConnectServiceException} only when Redis itself is unreachable. Publishing to a
 * dead instance's push channel silently drops.
 */
public class ConnectServiceRedisAdapter implements ConnectService {

  private static final Logger log = LoggerFactory.getLogger(ConnectServiceRedisAdapter.class);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public ConnectServiceRedisAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public RuntimeStatus pushOnInstance(UUID sourceId, List<byte[]> data, String instanceId)
      throws ConnectServiceException {
    var message = new PushMessage(sourceId, data);
    publish(PushMessage.channelFor(instanceId), objectMapper.writeValueAsString(message));

    return new RuntimeStatus(sourceId, ConnectorState.CONNECTED, null, instanceId);
  }

  private void publish(String channel, String json) throws ConnectServiceException {
    try {
      redisTemplate.convertAndSend(channel, json);
      log.debug("Published {} to channel {}", json, channel);
    } catch (RedisConnectionFailureException e) {
      log.error(
          "Redis unavailable, cannot publish PUSH payload to {}: {}", channel, e.getMessage());
      throw new ConnectServiceException(ConnectServiceExceptionCodes.SERVICE_UNAVAILABLE);
    } catch (Exception e) {
      log.error("Failed to publish PUSH payload to {}: {}", channel, e.getMessage());
      throw new ConnectServiceException(
          ConnectServiceExceptionCodes.SERVICE_UNAVAILABLE.code(),
          "Failed to publish PUSH payload: " + e.getMessage(),
          ConnectServiceExceptionCodes.SERVICE_UNAVAILABLE.httpStatus());
    }
  }
}
