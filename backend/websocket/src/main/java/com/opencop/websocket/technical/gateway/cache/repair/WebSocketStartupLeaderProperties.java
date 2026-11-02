// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("opencop.websocket.startup-leader")
public record WebSocketStartupLeaderProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("opencop:websocket:startup-leader") String lockKey) {
  public WebSocketStartupLeaderProperties {
    if (lockKey.isBlank())
      throw new IllegalArgumentException("Startup leader key must not be blank");
  }
}
