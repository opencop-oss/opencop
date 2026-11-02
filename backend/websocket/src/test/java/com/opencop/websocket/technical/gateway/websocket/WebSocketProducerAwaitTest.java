// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import com.opencop.websocket.technical.spring.websocket.OCMPMessagingTemplate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WebSocketProducerAwaitTest {

  @Test
  void sendToUserAwait_delegatesDirectlyToTemplate_sameFutureInstance() throws Exception {
    OCMPMessagingTemplate template = mock(OCMPMessagingTemplate.class);
    UUID userId = UUID.randomUUID();
    String destination = "/queue/history";
    byte[] payload = new byte[] {1, 2, 3};
    Map<String, String> headers = Map.of("x-key", "val");

    CompletableFuture<SendAttemptResult> expected =
        CompletableFuture.completedFuture(SendAttemptResult.DELIVERED);
    when(template.sendToUserAwait(userId, destination, payload, headers, false))
        .thenReturn(expected);

    WebSocketProducer producer = new WebSocketProducer(template);

    CompletableFuture<SendAttemptResult> actual =
        producer.sendToUserAwait(userId, destination, payload, headers, false);

    assertThat(actual).isSameAs(expected);
    assertThat(actual.get(1, TimeUnit.SECONDS)).isEqualTo(SendAttemptResult.DELIVERED);
  }
}
