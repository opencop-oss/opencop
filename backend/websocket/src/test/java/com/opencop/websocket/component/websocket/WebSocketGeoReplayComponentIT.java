// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout.SourceKeys;
import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.technical.gateway.cache.Janitor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import opencop.core.proto.websocket.SocketProtos.SocketMessage.MultiPayload;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketSession;

class WebSocketGeoReplayComponentIT extends AbstractWebSocketComponentIT {

  private static final String USER_MESSAGES = "/user/queue/messages";

  @MockitoBean private Janitor janitor;

  @Test
  void replay_emptySource_completesWithoutDataFrames() throws Exception {
    UUID sourceId = UUID.randomUUID();

    var replay = startReplay(sourceId);

    assertState(awaitMessage(replay.handler(), "empty replay start"), sourceId, "STARTING");
    assertState(awaitMessage(replay.handler(), "empty replay finish"), sourceId, "FINISHED");
    assertThat(replay.handler().messageCount()).isZero();
    assertThat(replay.session().isOpen()).isTrue();
  }

  @Test
  void replay_missingAndForeignPayloads_returnsSurvivorsInIndexOrder() throws Exception {
    UUID sourceId = UUID.randomUUID();
    SourceKeys sourceKeys = eventGeoCacheKeyLayout.sourceKeys(sourceId);
    SourceKeys foreignKeys = eventGeoCacheKeyLayout.sourceKeys(UUID.randomUUID());

    seedIndexedEvent(sourceKeys, "first", "first-event".getBytes(StandardCharsets.UTF_8), 1);
    seedIndexedMember(sourceKeys, foreignKeys.eventKey("foreign"), 2);
    redisTemplate.opsForValue().set(foreignKeys.eventKey("foreign"), bytes("foreign-event"));
    seedIndexedMember(sourceKeys, sourceKeys.eventKey("missing"), 3);
    seedIndexedEvent(sourceKeys, "last", bytes("last-event"), 4);

    var replay = startReplay(sourceId);

    assertState(awaitMessage(replay.handler(), "partial replay start"), sourceId, "STARTING");
    var data = awaitMessage(replay.handler(), "partial replay data");
    assertThat(data.getDestination()).isEqualTo(destination(sourceId));
    assertThat(data.getCompressed()).isFalse();
    assertThat(decodeMultiPayload(data)).containsExactly(bytes("first-event"), bytes("last-event"));
    assertState(awaitMessage(replay.handler(), "partial replay finish"), sourceId, "FINISHED");
    assertThat(replay.handler().messageCount()).isZero();
  }

  @Test
  void replay_wholeMissingPage_reachesNextLiveMember() throws Exception {
    UUID sourceId = UUID.randomUUID();
    SourceKeys sourceKeys = eventGeoCacheKeyLayout.sourceKeys(sourceId);
    int pageSize = 5_000;

    for (int index = 0; index <= pageSize; index++) {
      String objectId = "event-" + index;
      byte[] payload = index == pageSize ? bytes("live-after-missing-page") : null;
      seedIndexedEvent(sourceKeys, objectId, payload, index);
    }

    var replay = startReplay(sourceId);

    assertState(awaitMessage(replay.handler(), "missing-page replay start"), sourceId, "STARTING");
    var data = awaitMessage(replay.handler(), "member after missing page");
    assertThat(data.getDestination()).isEqualTo(destination(sourceId));
    assertThat(data.getCompressed()).isFalse();
    assertThat(decodeMultiPayload(data)).containsExactly(bytes("live-after-missing-page"));
    assertState(awaitMessage(replay.handler(), "missing-page replay finish"), sourceId, "FINISHED");
    assertThat(replay.handler().messageCount()).isZero();
  }

  @Test
  void replay_multiplePages_compressesOnlyPagesAbovePayloadThreshold() throws Exception {
    UUID sourceId = UUID.randomUUID();
    SourceKeys sourceKeys = eventGeoCacheKeyLayout.sourceKeys(sourceId);
    int eventCount = 5_001;
    var expected = new ArrayList<byte[]>(eventCount);

    for (int index = 0; index < eventCount; index++) {
      byte[] payload = bytes("event-" + index);
      expected.add(payload);
      seedIndexedEvent(sourceKeys, "event-" + index, payload, index);
    }

    var replay = startReplay(sourceId);

    assertState(awaitMessage(replay.handler(), "large replay start"), sourceId, "STARTING");
    var firstPage = awaitMessage(replay.handler(), "large replay first page");
    assertThat(firstPage.getDestination()).isEqualTo(destination(sourceId));
    assertThat(firstPage.getCompressed()).isTrue();
    assertThat(decodeMultiPayload(firstPage)).containsExactlyElementsOf(expected.subList(0, 5_000));

    var secondPage = awaitMessage(replay.handler(), "large replay second page");
    assertThat(secondPage.getDestination()).isEqualTo(destination(sourceId));
    assertThat(secondPage.getCompressed()).isFalse();
    assertThat(decodeMultiPayload(secondPage)).containsExactly(expected.getLast());

    assertState(awaitMessage(replay.handler(), "large replay finish"), sourceId, "FINISHED");
    assertThat(replay.handler().messageCount()).isZero();
  }

  @Test
  void replay_allPayloadsMissing_completesWithoutDataFrames() throws Exception {
    UUID sourceId = UUID.randomUUID();
    SourceKeys sourceKeys = eventGeoCacheKeyLayout.sourceKeys(sourceId);
    seedIndexedMember(sourceKeys, sourceKeys.eventKey("expired-1"), 1);
    seedIndexedMember(sourceKeys, sourceKeys.eventKey("expired-2"), 2);
    seedIndexedMember(sourceKeys, sourceKeys.eventKey("expired-3"), 3);

    var replay = startReplay(sourceId);

    assertState(awaitMessage(replay.handler(), "all-missing replay start"), sourceId, "STARTING");
    assertState(awaitMessage(replay.handler(), "all-missing replay finish"), sourceId, "FINISHED");
    assertThat(replay.handler().messageCount()).isZero();
  }

  private Replay startReplay(UUID sourceId) throws Exception {
    var handler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), handler);
    seedActiveSourceAccess(sourceId, awaitCurrentUserId());
    fixture.send(session, fixture.subscribe(USER_MESSAGES));
    fixture.send(session, fixture.subscribe(sourceId));
    return new Replay(session, handler);
  }

  private void seedIndexedEvent(
      SourceKeys sourceKeys, String objectId, byte[] payload, double score) {
    String eventKey = sourceKeys.eventKey(objectId);
    seedIndexedMember(sourceKeys, eventKey, score);
    if (payload != null) {
      redisTemplate.opsForValue().set(eventKey, payload);
    }
  }

  private void seedIndexedMember(SourceKeys sourceKeys, String member, double score) {
    stringRedisTemplate.opsForZSet().add(sourceKeys.updatedAtKey(), member, score);
  }

  private static void assertState(SocketMessage message, UUID sourceId, String state)
      throws Exception {
    assertThat(message.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(message.getDestination()).isEqualTo(USER_MESSAGES);
    assertThat(new String(message.getPayload().toByteArray(), StandardCharsets.UTF_8))
        .contains("\"source\":\"" + sourceId + "\"")
        .contains("\"state\":\"" + state + "\"");
  }

  private static List<byte[]> decodeMultiPayload(SocketMessage message) throws IOException {
    byte[] payload = message.getPayload().toByteArray();
    if (message.getCompressed()) {
      try (var input = new GZIPInputStream(new ByteArrayInputStream(payload))) {
        payload = input.readAllBytes();
      }
    }
    return MultiPayload.parseFrom(payload).getPayloadList().stream()
        .map(bytes -> bytes.toByteArray())
        .toList();
  }

  private static String destination(UUID sourceId) {
    return "/topic/queue/event/" + sourceId;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private record Replay(WebSocketSession session, RecordingBinaryWebSocketHandler handler) {}
}
