// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.broker;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.util.Map;

public class BrokerProducerMock<I> implements MessageBrokerProducer<I> {

  private final SendBehavior<I> sendBehavior;

  public BrokerProducerMock(SendBehavior<I> behavior) {
    this.sendBehavior = behavior;
  }

  @Override
  public void send(I event, Map<String, String> headers) {
    this.send(event);
  }

  @Override
  public void sendDlt(I event) {
    this.send(event);
  }

  @Override
  public void send(I event) {
    this.sendBehavior.run(this, event);
  }

  @FunctionalInterface
  public interface SendBehavior<I> {
    void run(BrokerProducerMock<I> mock, I event);
  }

  public static SendBehavior<?> onSendBehavior() {
    return ((_, _) -> {});
  }
}
