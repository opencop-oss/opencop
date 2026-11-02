// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrokerMessageTest {

  @Test
  void of_whenHeadersAreNull_normalizesToEmptyHeaders() {
    BrokerMessage<String> message = BrokerMessage.of("event", null);

    assertThat(message.event()).isEqualTo("event");
    assertThat(message.key()).isNull();
    assertThat(message.headers()).isEmpty();
  }

  @Test
  void of_defensivelyCopiesHeaders() {
    var headers = new HashMap<String, String>();
    headers.put("trace-id", "before");

    BrokerMessage<String> message = BrokerMessage.of("event", headers);
    headers.put("trace-id", "after");

    assertThat(message.headers()).containsEntry("trace-id", "before");
    assertThatThrownBy(() -> message.headers().put("trace-id", "mutated"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void of_whenEventOnly_setsEmptyHeaders() {
    BrokerMessage<String> message = BrokerMessage.of("event");

    assertThat(message.event()).isEqualTo("event");
    assertThat(message.key()).isNull();
    assertThat(message.headers()).isEqualTo(Map.of());
  }

  @Test
  void withKey_setsKeyAndHeaders() {
    BrokerMessage<String> message =
        BrokerMessage.withKey("source-key", "event", Map.of("trace-id", "abc"));

    assertThat(message.key()).isEqualTo("source-key");
    assertThat(message.event()).isEqualTo("event");
    assertThat(message.headers()).containsEntry("trace-id", "abc");
  }
}
