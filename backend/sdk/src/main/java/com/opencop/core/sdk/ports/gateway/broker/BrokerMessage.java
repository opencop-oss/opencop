// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.broker;

import java.util.Map;

public record BrokerMessage<T>(T event, String key, Map<String, String> headers) {

  public BrokerMessage {
    headers = headers == null ? Map.of() : Map.copyOf(headers);
  }

  public static <T> BrokerMessage<T> of(T event) {
    return new BrokerMessage<>(event, null, Map.of());
  }

  public static <T> BrokerMessage<T> of(T event, Map<String, String> headers) {
    return new BrokerMessage<>(event, null, headers);
  }

  public static <T> BrokerMessage<T> withKey(String key, T event, Map<String, String> headers) {
    return new BrokerMessage<>(event, key, headers);
  }
}
