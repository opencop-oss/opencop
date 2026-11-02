// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.websocket.ports.gateway.cache.geo.GeoEventReader;
import com.opencop.websocket.ports.gateway.websocket.SocketProducer;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class RetrieveAllEventsHandlerTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final String SOURCE = "source-1";
  private static final String DESTINATION = "/topic/queue/event/source-1";

  @Test
  void handle_advancesByConsumedMembersAndPreservesCommandPayloadOrderAndCompression()
      throws Exception {
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    var firstPagePayloads =
        IntStream.range(0, 1_001)
            .mapToObj(index -> new byte[] {(byte) index, (byte) (index >>> 8)})
            .toList();
    var secondPayload = new byte[] {9, 8};
    when(reader.readPage(SOURCE, 0, 5_000))
        .thenReturn(new GeoEventReader.Page(5_000, firstPagePayloads));
    when(reader.readPage(SOURCE, 5_000, 5_000))
        .thenReturn(new GeoEventReader.Page(1, List.of(secondPayload)));
    when(reader.readPage(SOURCE, 5_001, 5_000)).thenReturn(new GeoEventReader.Page(0, List.of()));

    new RetrieveAllEventsHandler(reader, producer, new ObjectMapper())
        .handle(USER_ID, SOURCE, DESTINATION);

    var destinations = ArgumentCaptor.forClass(String.class);
    var payloads = ArgumentCaptor.forClass(byte[].class);
    var compressions = ArgumentCaptor.forClass(Boolean.class);
    verify(producer, times(4))
        .sendToUser(
            eq(USER_ID), destinations.capture(), payloads.capture(), compressions.capture());
    assertThat(destinations.getAllValues())
        .containsExactly("/user/queue/messages", DESTINATION, DESTINATION, "/user/queue/messages");
    assertThat(compressions.getAllValues()).containsExactly(false, true, false, false);

    assertThat(jsonState(payloads.getAllValues().get(0))).isEqualTo("STARTING");
    var firstMessage = SocketMessage.MultiPayload.parseFrom(payloads.getAllValues().get(1));
    assertThat(firstMessage.getPayloadCount()).isEqualTo(1_001);
    assertThat(firstMessage.getPayload(0).toByteArray()).containsExactly(0, 0);
    assertThat(firstMessage.getPayload(1_000).toByteArray()).containsExactly((byte) 1_000, 3);
    var secondMessage = SocketMessage.MultiPayload.parseFrom(payloads.getAllValues().get(2));
    assertThat(secondMessage.getPayloadList()).hasSize(1);
    assertThat(secondMessage.getPayload(0).toByteArray()).containsExactly(9, 8);
    assertThat(jsonState(payloads.getAllValues().get(3))).isEqualTo("FINISHED");

    verify(reader).readPage(SOURCE, 0, 5_000);
    verify(reader).readPage(SOURCE, 5_000, 5_000);
    verify(reader).readPage(SOURCE, 5_001, 5_000);
  }

  @Test
  void handle_advancesThroughAllMissingPagesWithoutSendingEmptyPayloadFrames() throws Exception {
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    when(reader.readPage(SOURCE, 0, 5_000)).thenReturn(new GeoEventReader.Page(5_000, List.of()));
    when(reader.readPage(SOURCE, 5_000, 5_000)).thenReturn(new GeoEventReader.Page(1, List.of()));
    when(reader.readPage(SOURCE, 5_001, 5_000)).thenReturn(new GeoEventReader.Page(0, List.of()));

    new RetrieveAllEventsHandler(reader, producer, new ObjectMapper())
        .handle(USER_ID, SOURCE, DESTINATION);

    var destinations = ArgumentCaptor.forClass(String.class);
    verify(producer, times(2))
        .sendToUser(
            eq(USER_ID),
            destinations.capture(),
            ArgumentCaptor.forClass(byte[].class).capture(),
            anyBoolean());
    assertThat(destinations.getAllValues())
        .containsExactly("/user/queue/messages", "/user/queue/messages");
  }

  @Test
  void handle_propagatesReaderFailureWithoutEmittingFalseFinishedCommand() {
    var reader = mock(GeoEventReader.class);
    var producer = mock(SocketProducer.class);
    var failure = new IllegalStateException("redis unavailable");
    when(reader.readPage(SOURCE, 0, 5_000)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                new RetrieveAllEventsHandler(reader, producer, new ObjectMapper())
                    .handle(USER_ID, SOURCE, DESTINATION))
        .isSameAs(failure);

    verify(producer)
        .sendToUser(
            eq(USER_ID), eq("/user/queue/messages"), org.mockito.ArgumentMatchers.any(), eq(false));
    verify(producer, times(1))
        .sendToUser(eq(USER_ID), anyString(), org.mockito.ArgumentMatchers.any(), anyBoolean());
  }

  private static String jsonState(byte[] payload) throws Exception {
    return new ObjectMapper().readTree(payload).get("state").asText();
  }
}
