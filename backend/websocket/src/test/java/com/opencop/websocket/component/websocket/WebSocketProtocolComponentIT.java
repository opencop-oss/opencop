// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.HEARTBEAT_EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeys;
import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class WebSocketProtocolComponentIT extends AbstractWebSocketComponentIT {

  // Happy path
  @Test
  void subscribesAuthorizedTopicAndLoadsInitialEventsFromRedis() throws Exception {
    assertThat(pingDatabase()).isEqualTo(1);

    UUID sourceId = UUID.randomUUID();
    String eventKey1 = EventGeoCacheKeys.event(sourceId, "event-1");
    String eventKey2 = EventGeoCacheKeys.event(sourceId, "event-2");
    String updatedAtKey = EventGeoCacheKeys.updatedAt(sourceId);
    byte[] event1 = "first-event".getBytes(StandardCharsets.UTF_8);
    byte[] event2 = "second-event".getBytes(StandardCharsets.UTF_8);

    redisTemplate.opsForValue().set(eventKey1, event1);
    redisTemplate.opsForValue().set(eventKey2, event2);
    stringRedisTemplate.opsForZSet().add(updatedAtKey, eventKey1, 1d);
    stringRedisTemplate.opsForZSet().add(updatedAtKey, eventKey2, 2d);

    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedActiveSourceAccess(sourceId, awaitCurrentUserId());

    fixture.send(session, fixture.subscribe("/user/queue/messages"));
    fixture.send(session, fixture.subscribe(sourceId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "topic subscription registration");

    var startCommand = awaitMessage(clientHandler, "subscription start command");
    assertThat(startCommand.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(startCommand.getDestination()).isEqualTo("/user/queue/messages");
    assertThat(readJson(startCommand.getPayload().toByteArray()).get("state").asText())
        .isEqualTo("STARTING");
    assertThat(readJson(startCommand.getPayload().toByteArray()).get("source").asText())
        .isEqualTo(sourceId.toString());

    var initEvents = awaitMessage(clientHandler, "initial event replay");
    assertThat(initEvents.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(initEvents.getDestination()).isEqualTo("/topic/queue/event/" + sourceId);
    assertThat(initEvents.getCompressed()).isFalse();
    assertThat(readMultiPayload(initEvents)).containsExactly(event1, event2);

    var finishCommand = awaitMessage(clientHandler, "subscription finish command");
    assertThat(finishCommand.getType()).isEqualTo(SocketMessage.Type.SEND);
    assertThat(finishCommand.getDestination()).isEqualTo("/user/queue/messages");
    assertThat(readJson(finishCommand.getPayload().toByteArray()).get("state").asText())
        .isEqualTo("FINISHED");
    assertThat(readJson(finishCommand.getPayload().toByteArray()).get("source").asText())
        .isEqualTo(sourceId.toString());
  }

  // Subscription authorization edge cases
  @Test
  void rejectsUnauthorizedTopicSubscriptionAndKeepsSessionAlive() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID sourceId = UUID.randomUUID();
    awaitSingleUserSession();

    fixture.send(session, fixture.subscribe(sourceId));

    var rejection = awaitMessage(clientHandler, "subscription rejection");
    assertThat(rejection.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap()).containsEntry("operation", "SUBSCRIBE");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + sourceId);
    assertThat(rejection.getPayload().toStringUtf8()).isEqualTo("Insufficient access");
    assertThat(session.isOpen()).isTrue();
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId)).isEmpty();
  }

  @Test
  void rejectsSourceTopicSubscriptionWhenSourceRowIsMissing() throws Exception {
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID currentUserId = awaitCurrentUserId();
    seedSourceAccess(sourceId, currentUserId);

    fixture.send(session, fixture.subscribe(sourceId));

    var rejection = awaitMessage(clientHandler, "missing source row subscription rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + sourceId);
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void authorizesLayerTopicSubscriptionFromPersistedLayerMembership() throws Exception {
    UUID layerId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedLayerAccess(layerId, awaitCurrentUserId());

    fixture.send(session, fixture.subscribe(layerId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + layerId).isEmpty(),
        fixture.defaultTimeout(),
        "layer topic subscription registration");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsSourceTopicSubscriptionForArchivedSourceAndKeepsSessionAlive() throws Exception {
    UUID sourceId = UUID.randomUUID();
    UUID archivedBy = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID currentUserId = awaitCurrentUserId();
    seedArchivedSourceAccess(sourceId, currentUserId, archivedBy);

    fixture.send(session, fixture.subscribe(sourceId));

    var rejection = awaitMessage(clientHandler, "archived source subscription rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + sourceId);
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void authorizesSourceTopicSubscriptionFromDataSourceGroupMembership() throws Exception {
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedDataSourceGroupSourceAccess(sourceId, awaitCurrentUserId());

    fixture.send(session, fixture.subscribe(sourceId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "data-source-group source topic subscription registration");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void authorizesSourceTopicSubscriptionFromGlobalLayerEmbeddedDataSourceGroup() throws Exception {
    UUID layerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    seedGlobalLayerDataSourceGroupSourceAccess(layerId, sourceId);

    fixture.send(session, fixture.subscribe(sourceId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "global-layer embedded data-source-group source topic subscription registration");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsDataSourceGroupSourceSubscriptionAfterSourceIsArchived() throws Exception {
    UUID sourceId = UUID.randomUUID();
    UUID archivedBy = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedDataSourceGroupSourceAccess(sourceId, awaitCurrentUserId());
    archiveSeededSource(sourceId, archivedBy);

    fixture.send(session, fixture.subscribe(sourceId));

    assertSourceSubscriptionDenied(clientHandler, sourceId, "archived data-source-group source");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsGlobalLayerEmbeddedDataSourceGroupSourceSubscriptionAfterLayerIsInactive()
      throws Exception {
    UUID layerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    seedGlobalLayerDataSourceGroupSourceAccess(layerId, sourceId);
    updateSeededLayerLifecycle(layerId, "MERGE_SOURCE_LOCKED");

    fixture.send(session, fixture.subscribe(sourceId));

    assertSourceSubscriptionDenied(
        clientHandler, sourceId, "inactive global-layer embedded data-source-group source");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsGlobalLayerEmbeddedDataSourceGroupSourceSubscriptionAfterLayerIsArchived()
      throws Exception {
    UUID layerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    UUID archivedBy = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    seedGlobalLayerDataSourceGroupSourceAccess(layerId, sourceId);
    archiveSeededLayer(layerId, archivedBy);

    fixture.send(session, fixture.subscribe(sourceId));

    assertSourceSubscriptionDenied(
        clientHandler, sourceId, "archived global-layer embedded data-source-group source");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsGlobalLayerEmbeddedDataSourceGroupSourceSubscriptionAfterEmbeddingIsRemoved()
      throws Exception {
    UUID layerId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    UUID dataSourceGroupId = seedGlobalLayerDataSourceGroupSourceAccess(layerId, sourceId);
    removeLayerDataSourceGroupEmbedding(layerId, dataSourceGroupId);

    fixture.send(session, fixture.subscribe(sourceId));

    assertSourceSubscriptionDenied(
        clientHandler, sourceId, "removed global-layer data-source-group embedding source");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsLayerTopicSubscriptionForArchivedLayerAndKeepsSessionAlive() throws Exception {
    UUID layerId = UUID.randomUUID();
    UUID archivedBy = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID currentUserId = awaitCurrentUserId();
    seedLayerAccess(layerId, currentUserId);
    archiveSeededLayer(layerId, archivedBy);

    fixture.send(session, fixture.subscribe(layerId));

    var rejection = awaitMessage(clientHandler, "archived layer subscription rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + layerId);
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + layerId)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsLayerTopicSubscriptionForNonActiveLayerAndKeepsSessionAlive() throws Exception {
    UUID layerId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID currentUserId = awaitCurrentUserId();
    seedLayerAccess(layerId, currentUserId);
    updateSeededLayerLifecycle(layerId, "MERGE_SOURCE_LOCKED");

    fixture.send(session, fixture.subscribe(layerId));

    var rejection = awaitMessage(clientHandler, "non-active layer subscription rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + layerId);
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + layerId)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void authorizesLayerTopicSubscriptionFromGroupMembership() throws Exception {
    UUID layerId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedLayerAccessViaGroup(layerId, awaitCurrentUserId(), "MEMBER");

    fixture.send(session, fixture.subscribe(layerId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + layerId).isEmpty(),
        fixture.defaultTimeout(),
        "group-based layer topic subscription registration");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsLayerTopicSubscriptionWhenUserHasNoDirectOrGroupAccess() throws Exception {
    UUID layerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer(id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(session, fixture.subscribe(layerId));

    var rejection = awaitMessage(clientHandler, "group-less layer rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + layerId)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  // Invalid frame or destination handling
  @Test
  void acceptsOnlyTheSupportedUserDestinationTable() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    for (String destination :
        java.util.List.of(
            "/user/queue/history", "/user/queue/messages", "/user/queue/notification")) {
      fixture.send(session, fixture.subscribe(destination));
      await(
          () -> userSession.isSubscribed(destination),
          fixture.defaultTimeout(),
          "supported user destination subscription");
    }

    fixture.send(session, fixture.subscribe("/user/queue/errors"));

    var rejection = awaitMessage(clientHandler, "removed error destination rejection");
    assertProtocolError(
        rejection, "INVALID_DESTINATION", "SUBSCRIBE", "Invalid destination", "/user/queue/errors");
  }

  @Test
  void rejectsRepresentativeNoncanonicalEventDestinations() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    String canonical = "4b0da4e4-7a7f-45f1-8bf9-575f6ba5a76c";

    for (String destination :
        java.util.List.of(
            "/topic/queue/event/not-a-uuid",
            "/topic/queue/event/" + canonical.toUpperCase(),
            "/topic/queue/event/" + canonical + "/extra",
            "/topic/queue/event/" + canonical + "?query=true")) {
      fixture.send(session, fixture.subscribe(destination));
      assertProtocolError(
          awaitMessage(clientHandler, "noncanonical topic rejection"),
          "INVALID_TOPIC",
          "SUBSCRIBE",
          "Invalid topic destination id",
          destination);
    }
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void appliesUtf8DestinationBoundsAndTheSameGrammarToUnsubscribe() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    String bytes128 = "é".repeat(64);
    String bytes129 = bytes128 + "a";

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.UNSUBSCRIBE)
            .setDestination(bytes128)
            .build());
    assertProtocolError(
        awaitMessage(clientHandler, "128-byte unsubscribe rejection"),
        "INVALID_DESTINATION",
        "UNSUBSCRIBE",
        "Invalid destination",
        bytes128);

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.UNSUBSCRIBE)
            .setDestination(bytes129)
            .build());
    assertProtocolError(
        awaitMessage(clientHandler, "129-byte unsubscribe rejection"),
        "INVALID_DESTINATION",
        "UNSUBSCRIBE",
        "Invalid destination",
        null);
  }

  @Test
  void validUnsubscribeFromInactiveDestinationIsAnIdempotentNoOp() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.UNSUBSCRIBE)
            .setDestination("/user/queue/history")
            .build());

    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    assertThat(userSession.isSubscribed("/user/queue/history")).isFalse();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsInvalidKnownFieldsAndUnsupportedTypesBeforeStateChanges() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SUBSCRIBE)
            .setDestination("/user/queue/history")
            .setPayload(ByteString.copyFromUtf8("unused"))
            .build());
    assertProtocolError(
        awaitMessage(clientHandler, "invalid known-field rejection"),
        "INVALID_FRAME",
        "SUBSCRIBE",
        "Invalid frame",
        null);
    assertThat(userSession.isSubscribed("/user/queue/history")).isFalse();

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.CONNECT)
            .setDestination("must-not-be-reflected")
            .build());
    assertProtocolError(
        awaitMessage(clientHandler, "unsupported type rejection"),
        "UNSUPPORTED_FRAME_TYPE",
        "CONNECT",
        "Frame type not supported",
        null);

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setTypeValue(999)
            .setDestination("must-not-be-reflected")
            .build());
    assertProtocolError(
        awaitMessage(clientHandler, "unrecognized type rejection"),
        "UNSUPPORTED_FRAME_TYPE",
        "UNKNOWN",
        "Frame type not supported",
        null);
  }

  @Test
  void rejectsInvalidDestinationWithoutRegisteringTopic() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(session, fixture.subscribe("/bad/destination"));

    var rejection = awaitMessage(clientHandler, "invalid destination rejection");
    assertThat(rejection.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getCompressed()).isFalse();
    assertThat(rejection.getHeadersMap())
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of(
                "code",
                "INVALID_DESTINATION",
                "operation",
                "SUBSCRIBE",
                "rejected-destination",
                "/bad/destination"));
    assertThat(rejection.getPayload().toStringUtf8()).isEqualTo("Invalid destination");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsUnknownTopicFamiliesWithoutRegisteringDestination() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    String unknownFamily = "/topic/mystery/" + UUID.randomUUID();

    fixture.send(session, fixture.subscribe(unknownFamily));

    var rejection = awaitMessage(clientHandler, "unknown topic family rejection");
    assertProtocolError(
        rejection, "UNKNOWN_TOPIC", "SUBSCRIBE", "Unknown topic destination", unknownFamily);
    assertThat(sessionRegistry.getDestinationSessions(unknownFamily)).isEmpty();

    // The live-event gate matches the exact prefix only; an embedded "/queue/event/" segment
    // is not a live-event destination and no broadcast ever targets it.
    String nestedLiveEvent = "/topic/x/queue/event/" + UUID.randomUUID();
    fixture.send(session, fixture.subscribe(nestedLiveEvent));

    var nestedRejection = awaitMessage(clientHandler, "nested live-event prefix rejection");
    assertProtocolError(
        nestedRejection,
        "UNKNOWN_TOPIC",
        "SUBSCRIBE",
        "Unknown topic destination",
        nestedLiveEvent);
    assertThat(sessionRegistry.getDestinationSessions(nestedLiveEvent)).isEmpty();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void malformedBinaryFramesCloseSessionAsBadData() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    fixture.send(session, new byte[] {0x01, 0x02, 0x03});

    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    var closeStatus = awaitCloseStatus(clientHandler, "malformed frame close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    await(
        () -> !sessionRegistry.getUsersSessions().contains(userSession),
        fixture.defaultTimeout(),
        "malformed frame registry cleanup");
    assertThat(session.isOpen()).isFalse();
  }

  @Test
  void completeTextMessageAtTheDefaultBoundaryReachesTheHandlerAndIsRejectedGenerically()
      throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.sendText(session, "x".repeat(65_536));

    assertProtocolError(
        awaitMessage(clientHandler, "boundary text rejection"),
        "UNSUPPORTED_MESSAGE_TYPE",
        "TEXT",
        "Text messages not supported",
        null);
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void fragmentedTextMessagesUseTheSameCompleteMessageBoundary() throws Exception {
    var boundaryHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession boundarySession = fixture.connect(websocketUrl(), boundaryHandler);
    awaitSingleUserSession();

    fixture.sendText(boundarySession, "a".repeat(32_768), false);
    fixture.sendText(boundarySession, "b".repeat(32_768), true);

    assertProtocolError(
        awaitMessage(boundaryHandler, "fragmented boundary text rejection"),
        "UNSUPPORTED_MESSAGE_TYPE",
        "TEXT",
        "Text messages not supported",
        null);
    assertThat(boundarySession.isOpen()).isTrue();

    boundarySession.close();
    awaitNoRegisteredSessions();
    var oversizedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession oversizedSession = fixture.connect(websocketUrl(), oversizedHandler);
    awaitSingleUserSession();

    fixture.sendText(oversizedSession, "a".repeat(32_768), false);
    fixture.sendText(oversizedSession, "b".repeat(32_769), true);

    var closeStatus = awaitCloseStatus(oversizedHandler, "fragmented oversized text close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    assertThat(oversizedHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    awaitNoRegisteredSessions();
  }

  @Test
  void completeBinaryMessageAtTheDefaultBoundaryReachesProtobufParsing() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(session, new byte[65_536]);

    var closeStatus = awaitCloseStatus(clientHandler, "boundary malformed binary close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    awaitNoRegisteredSessions();
  }

  @Test
  void oversizedBinaryMessageClosesOnlyItsSocketBeforeCreatingSubscriptionState() throws Exception {
    UUID sourceId = UUID.randomUUID();
    var oversizedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession oversizedSession = fixture.connect(websocketUrl(), oversizedHandler);
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthySession = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two registered sessions");
    byte[] attemptedSubscription = fixture.subscribe(sourceId).toByteArray();

    fixture.send(oversizedSession, Arrays.copyOf(attemptedSubscription, 65_537));

    var closeStatus = awaitCloseStatus(oversizedHandler, "oversized binary close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "oversized session cleanup");
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId)).isEmpty();
    assertThat(healthySession.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();
  }

  @Test
  void pongControlMessageProducesNoProtocolErrorAndKeepsTheSocketOpen() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.sendPong(session, new byte[] {0x01, 0x02});

    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    assertThat(clientHandler.closeStatus()).isNull();
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void rejectsSendFramesBecauseSendIsNotSupported() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(
        session,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SEND)
            .setDestination("/topic/queue/event/unsupported")
            .build());

    var rejection = awaitMessage(clientHandler, "send rejection");
    assertThat(rejection.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap())
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of("code", "SEND_NOT_SUPPORTED", "operation", "SEND"));
    assertThat(rejection.getPayload().toStringUtf8()).isEqualTo("SEND not supported");
  }

  @Test
  void closesOnlyTheTenthViolationAndReconnectsWithFreshHistory() throws Exception {
    var violatorHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession violator = fixture.connect(websocketUrl(), violatorHandler);
    var violatingUserSession = awaitSingleUserSession();
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthy = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two sockets before protocol violations");
    String validDestination = "/user/queue/notification";
    fixture.send(violator, fixture.subscribe(validDestination));
    await(
        () -> violatingUserSession.isSubscribed(validDestination),
        fixture.defaultTimeout(),
        "valid subscription before protocol violations");

    var violations =
        List.of(
            new ExpectedViolation(
                SocketMessage.newBuilder()
                    .setType(SocketMessage.Type.SUBSCRIBE)
                    .setDestination("/bad-subscribe")
                    .build(),
                "INVALID_DESTINATION",
                "SUBSCRIBE",
                "Invalid destination",
                "/bad-subscribe"),
            new ExpectedViolation(
                SocketMessage.newBuilder()
                    .setType(SocketMessage.Type.UNSUBSCRIBE)
                    .setDestination("/bad-unsubscribe")
                    .build(),
                "INVALID_DESTINATION",
                "UNSUBSCRIBE",
                "Invalid destination",
                "/bad-unsubscribe"),
            new ExpectedViolation(
                SocketMessage.newBuilder()
                    .setType(SocketMessage.Type.HEARTBEAT)
                    .setPayload(ByteString.copyFromUtf8("private heartbeat payload"))
                    .build(),
                "INVALID_FRAME",
                "HEARTBEAT",
                "Invalid frame",
                null),
            new ExpectedViolation(
                SocketMessage.newBuilder()
                    .setType(SocketMessage.Type.SEND)
                    .setDestination("/private/send-destination")
                    .build(),
                "SEND_NOT_SUPPORTED",
                "SEND",
                "SEND not supported",
                null),
            unsupportedViolation(SocketMessage.Type.CONNECT, "CONNECT"),
            unsupportedViolation(SocketMessage.Type.ERROR, "ERROR"),
            unsupportedViolation(SocketMessage.Type.UNKNOWN, "UNKNOWN"),
            new ExpectedViolation(
                SocketMessage.newBuilder().setTypeValue(999).build(),
                "UNSUPPORTED_FRAME_TYPE",
                "UNKNOWN",
                "Frame type not supported",
                null));

    for (ExpectedViolation violation : violations) {
      fixture.send(violator, violation.message());
      assertProtocolError(
          awaitMessage(violatorHandler, violation.operation() + " violation"),
          violation.code(),
          violation.operation(),
          violation.reason(),
          violation.rejectedDestination());
      fixture.send(violator, fixture.heartbeat());
    }

    fixture.sendText(violator, "private text content");
    assertProtocolError(
        awaitMessage(violatorHandler, "text violation"),
        "UNSUPPORTED_MESSAGE_TYPE",
        "TEXT",
        "Text messages not supported",
        null);
    fixture.send(violator, fixture.heartbeat());

    fixture.send(violator, fixture.subscribe("/bad-final"));

    var closeStatus = awaitCloseStatus(violatorHandler, "protocol threshold close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    assertThat(closeStatus.getReason()).isEqualTo("Protocol limit exceeded");
    assertThat(violatorHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "threshold socket cleanup");
    assertThat(sessionRegistry.getDestinationSessions("/bad-final")).isEmpty();
    assertThat(violatingUserSession.isSubscribed(validDestination)).isFalse();
    assertThat(healthy.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();

    var reconnectedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession reconnected = fixture.connect(websocketUrl(), reconnectedHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "fresh socket registration");
    fixture.send(
        reconnected,
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SEND)
            .setDestination("/private/reconnected-send")
            .build());

    assertProtocolError(
        awaitMessage(reconnectedHandler, "fresh socket first violation"),
        "SEND_NOT_SUPPORTED",
        "SEND",
        "SEND not supported",
        null);
    assertThat(reconnected.isOpen()).isTrue();
  }

  // User delivery and broadcast flow
  @Test
  void unsubscribesTopicAndRemovesRegistryMembership() throws Exception {
    UUID sourceId = UUID.randomUUID();
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    seedActiveSourceAccess(sourceId, awaitCurrentUserId());

    fixture.send(session, fixture.subscribe(sourceId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "topic subscription registration");

    fixture.send(session, fixture.unsubscribe(sourceId));

    await(
        () -> sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId).isEmpty(),
        fixture.defaultTimeout(),
        "topic subscription removal");
  }

  // Disconnect, expiry, and cleanup
  @Test
  void heartbeatFrameReschedulesExpiryAndKeepsSessionOpen() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();
    var initialDeadline = Instant.now().plusSeconds(1);

    sessionExpiryKiller.scheduleClose(
        userSession, initialDeadline, 0, SessionExpiryKiller.HEARTBEAT);
    fixture.send(session, fixture.heartbeat());

    await(
        () -> Instant.now().isAfter(initialDeadline.plusMillis(250)),
        Duration.ofSeconds(2),
        "heartbeat expiry replacement deadline");

    assertThat(clientHandler.closeStatus()).isNull();
    assertThat(session.isOpen()).isTrue();
    assertThat(sessionRegistry.getUsersSessions()).contains(userSession);
  }

  @Test
  void closesExpiredHeartbeatSessionsAndRemovesThemFromTheRegistry() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();
    String setupBarrierDestination = "/user/queue/notification";

    fixture.send(session, fixture.subscribe(setupBarrierDestination));
    await(
        () -> userSession.isSubscribed(setupBarrierDestination),
        fixture.defaultTimeout(),
        "connection setup completion");

    sessionExpiryKiller.scheduleClose(
        userSession, Instant.now().plusMillis(200), 0, SessionExpiryKiller.HEARTBEAT);

    var closeStatus = awaitCloseStatus(clientHandler, "heartbeat expiry close");
    assertThat(closeStatus.getCode()).isEqualTo(HEARTBEAT_EXPIRED);
    awaitNoRegisteredSessions();
    assertThat(session.isOpen()).isFalse();
  }

  private void assertSourceSubscriptionDenied(
      RecordingBinaryWebSocketHandler clientHandler, UUID sourceId, String description)
      throws Exception {
    var rejection = awaitMessage(clientHandler, description + " subscription rejection");
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap())
        .containsEntry("rejected-destination", "/topic/queue/event/" + sourceId);
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + sourceId)).isEmpty();
  }

  private static void assertProtocolError(
      SocketMessage error,
      String code,
      String operation,
      String reason,
      String rejectedDestination) {
    assertThat(error.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(error.getDestination()).isEmpty();
    assertThat(error.getCompressed()).isFalse();
    assertThat(error.getHeadersMap())
        .containsEntry("code", code)
        .containsEntry("operation", operation);
    if (rejectedDestination == null) {
      assertThat(error.getHeadersMap()).doesNotContainKey("rejected-destination");
    } else {
      assertThat(error.getHeadersMap()).containsEntry("rejected-destination", rejectedDestination);
    }
    assertThat(error.getPayload().toStringUtf8()).isEqualTo(reason);
  }

  private static ExpectedViolation unsupportedViolation(SocketMessage.Type type, String operation) {
    return new ExpectedViolation(
        SocketMessage.newBuilder().setType(type).build(),
        "UNSUPPORTED_FRAME_TYPE",
        operation,
        "Frame type not supported",
        null);
  }

  private record ExpectedViolation(
      SocketMessage message,
      String code,
      String operation,
      String reason,
      String rejectedDestination) {}
}
