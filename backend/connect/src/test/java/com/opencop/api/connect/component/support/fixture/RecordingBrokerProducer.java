// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.fixture;

import com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

public final class RecordingBrokerProducer implements MessageBrokerProducer<byte[]> {

  private final Queue<byte[]> messages = new ArrayDeque<>();

  @Override
  public synchronized void send(byte[] event, Map<String, String> headers) {
    send(event);
  }

  @Override
  public synchronized void sendDlt(byte[] event) {
    send(event);
  }

  @Override
  public synchronized void send(byte[] event) {
    messages.add(event.clone());
    notifyAll();
  }

  public synchronized byte[] awaitMessage(Duration timeout) throws InterruptedException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (messages.isEmpty() && System.nanoTime() < deadlineNanos) {
      long remainingMillis = Duration.ofNanos(deadlineNanos - System.nanoTime()).toMillis();
      wait(Math.max(1, remainingMillis));
    }
    if (messages.isEmpty()) {
      throw new AssertionError("Timed out waiting for forwarded broker message");
    }
    return messages.remove();
  }

  public synchronized int pendingMessageCount() {
    return messages.size();
  }

  public synchronized void assertNoMessage(Duration timeout) throws InterruptedException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      if (!messages.isEmpty()) {
        throw new AssertionError("Expected no broker message within " + timeout);
      }
      wait(10);
    }
  }
}
