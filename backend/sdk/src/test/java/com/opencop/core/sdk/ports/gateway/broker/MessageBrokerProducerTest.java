// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageBrokerProducerTest {

  @Test
  void sendAll_defaultImplementationLoopsThroughMessagesWithHeaders() {
    var producer = new RecordingProducer();

    producer.sendAll(
        List.of(
            BrokerMessage.withKey("ignored-key", "one", Map.of("trace-id", "a")),
            BrokerMessage.of("two")));

    assertThat(producer.sent)
        .containsExactly(
            new SentMessage("one", Map.of("trace-id", "a")), new SentMessage("two", Map.of()));
  }

  private static final class RecordingProducer implements MessageBrokerProducer<String> {
    private final List<SentMessage> sent = new ArrayList<>();

    @Override
    public void send(String event, Map<String, String> headers) {
      sent.add(new SentMessage(event, headers));
    }

    @Override
    public void sendDlt(String event) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void send(String event) {
      throw new UnsupportedOperationException();
    }
  }

  private record SentMessage(String event, Map<String, String> headers) {}
}
