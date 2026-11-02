// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.broker;

import org.springframework.messaging.Message;

public interface MessageBrokerListener<I> {

  void onMessage(Message<I> message);
}
