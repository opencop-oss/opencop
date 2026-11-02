// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import java.util.List;
import java.util.Set;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GeoEventHandlerTest {

  private static final String SOURCE = "source:one";
  private static final String DESTINATION = "/topic/queue/event/%s";

  @Test
  void handle_readsSelectedObjectsAndBroadcastsOrderedUncompressedPayloads() throws Exception {
    var sessionRegistry = mock(WsSessionRegistry.class);
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    var session = mock(UserSession.class);
    when(sessionRegistry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(session));
    var first = new byte[] {1, 2};
    var second = new byte[] {3, 4};
    when(reader.readObjects(SOURCE, List.of("first", "second"))).thenReturn(List.of(first, second));

    var handler = new GeoEventHandler(sessionRegistry, reader, producer);
    handler.handle(SOURCE, List.of("first", "second"), DESTINATION);

    var topicCaptor = ArgumentCaptor.forClass(String.class);
    var payloadCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(producer).broadcast(topicCaptor.capture(), payloadCaptor.capture(), eq(false));
    assertThat(topicCaptor.getValue()).isEqualTo("/topic/queue/event/source/one");
    var message = SocketMessage.MultiPayload.parseFrom(payloadCaptor.getValue());
    assertThat(message.getPayloadCount()).isEqualTo(2);
    assertThat(message.getPayload(0).toByteArray()).containsExactly(1, 2);
    assertThat(message.getPayload(1).toByteArray()).containsExactly(3, 4);
  }

  @Test
  void handle_keepsNoSubscriberAndEmptyNotificationGuardsBeforeReading() {
    var sessionRegistry = mock(WsSessionRegistry.class);
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    var handler = new GeoEventHandler(sessionRegistry, reader, producer);

    when(sessionRegistry.getDestinationSessions(DESTINATION)).thenReturn(Set.of());
    handler.handle(SOURCE, List.of("first"), DESTINATION);
    verifyNoInteractions(reader, producer);

    when(sessionRegistry.getDestinationSessions(DESTINATION))
        .thenReturn(Set.of(mock(UserSession.class)));
    handler.handle(SOURCE, List.of(), DESTINATION);
    verifyNoInteractions(reader, producer);
  }

  @Test
  void handle_doesNotBroadcastWhenAllNotifiedPayloadsAreMissing() {
    var sessionRegistry = mock(WsSessionRegistry.class);
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    when(sessionRegistry.getDestinationSessions(DESTINATION))
        .thenReturn(Set.of(mock(UserSession.class)));
    when(reader.readObjects(SOURCE, List.of("missing"))).thenReturn(List.of());

    new GeoEventHandler(sessionRegistry, reader, producer)
        .handle(SOURCE, List.of("missing"), DESTINATION);

    verify(producer, never()).broadcast(any(), any(), anyBoolean());
  }
}
