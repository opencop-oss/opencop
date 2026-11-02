// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.opencop.websocket.technical.spring.config.WebSocketProtocolProperties;
import java.time.Duration;

public final class WebSocketProtocolTestFixtures {

  private WebSocketProtocolTestFixtures() {}

  public static ProtocolViolationTracker protocolViolationTracker() {
    return new ProtocolViolationTracker(10, Duration.ofSeconds(60));
  }

  public static UserSessionFactory userSessionFactory() {
    var properties = new WebSocketProtocolProperties();
    properties.afterPropertiesSet();
    return new UserSessionFactory(properties);
  }
}
