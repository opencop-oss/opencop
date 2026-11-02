// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.broker;

import java.util.Collection;
import java.util.Map;

public interface MessageBrokerProducer<I> {

  void send(I event, Map<String, String> headers);

  void sendDlt(I event);

  void send(I event);

  default void sendAll(Collection<BrokerMessage<I>> messages) {
    messages.forEach(message -> send(message.event(), message.headers()));
  }
}
