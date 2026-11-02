// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoryJobFailedEvent;
import com.opencop.websocket.ports.business.usecase.history.HistoryEventHandler;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import tools.jackson.databind.ObjectMapper;

class HistoryEventsListenerTest {

  @Test
  void onMessage_whenPayloadIsMalformed_doesNotThrow() {
    var objectMapper = new ObjectMapper();
    var handler = mock(HistoryEventHandler.class);
    var listener = new HistoryEventsListener(objectMapper, handler);

    assertDoesNotThrow(
        () ->
            listener.onMessage(
                new DefaultMessage("{".getBytes(StandardCharsets.UTF_8), new byte[0]),
                new byte[0]));
  }

  @Test
  void onMessage_whenHandlerThrows_runtimeFailureIsContained() throws Exception {
    var objectMapper = mock(ObjectMapper.class);
    var handler = new ThrowingHistoryEventHandler();
    var listener = new HistoryEventsListener(objectMapper, handler);
    var event =
        new HistoryJobFailedEvent(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440100"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
            SubjectType.SOURCE,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
            "DELIVERY_FAILED",
            "boom");

    org.mockito.Mockito.when(
            objectMapper.readValue(
                any(String.class), eq(com.opencop.core.sdk.model.history.HistoryEvent.class)))
        .thenReturn(event);

    assertDoesNotThrow(
        () ->
            listener.onMessage(
                new DefaultMessage("{}".getBytes(StandardCharsets.UTF_8), new byte[0]),
                new byte[0]));
    assertTrue(handler.called);
  }

  private static final class ThrowingHistoryEventHandler implements HistoryEventHandler {
    private boolean called;

    @Override
    public void handle(com.opencop.core.sdk.model.history.HistoryEvent event) {
      called = true;
      throw new IllegalStateException("boom");
    }
  }
}
