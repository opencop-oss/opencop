// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import com.opencop.core.sdk.helper.InstanceIdentity;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Matches Core/Processor's one-shot election: cached forever, no renewal or takeover, fail-open. A
 * late replica can become another leader; a replacement inside the TTL can remain non-leader.
 * Duplicate repair is safe but repeats scan work. This only gates directory repair.
 */
public final class WebSocketStartupLeader {
  private static final Logger log = LoggerFactory.getLogger(WebSocketStartupLeader.class);
  private final boolean leader;

  public WebSocketStartupLeader(
      StringRedisTemplate redis, WebSocketStartupLeaderProperties properties) {
    if (!properties.enabled()) {
      leader = true;
      return;
    }
    boolean acquired;
    try {
      acquired =
          Boolean.TRUE.equals(
              redis
                  .opsForValue()
                  .setIfAbsent(
                      properties.lockKey(),
                      InstanceIdentity.resolveInstanceId("websocket"),
                      Duration.ofMinutes(5)));
    } catch (RuntimeException exception) {
      acquired = true;
      log.warn("WebSocket startup leader election failed; assuming leader");
    }
    leader = acquired;
    log.info("WebSocket startup leader: {}", leader);
  }

  public boolean isLeader() {
    return leader;
  }
}
