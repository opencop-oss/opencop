// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.cache.CacheChannel;
import com.opencop.core.sdk.model.watchbox.WatchboxAlertEvent;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketSession;

// Short re-auth interval so the delivery-path sweep is observable within a test timeout.
@TestPropertySource(properties = "opencop.watchbox.subscription-reauth-interval=PT1S")
class WatchboxSubscribeComponentIT extends AbstractWebSocketComponentIT {

  @ParameterizedTest
  @CsvSource({"PRIVATE,PRIVATE", "PRIVATE,GLOBAL", "GLOBAL,GLOBAL"})
  void subscribe_readableLayerScopes_receivesAlertEvents(
      LayerAccessScope firstScope, LayerAccessScope secondScope) throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    UUID layerA = UUID.randomUUID();
    UUID layerB = UUID.randomUUID();
    seedReadableLayer(layerA, userId, firstScope);
    seedReadableLayer(layerB, userId, secondScope);
    UUID watchboxId = seedWatchbox(layerA, layerB);

    fixture.send(session, fixture.subscribe("/topic/watchbox/" + watchboxId));
    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/watchbox/" + watchboxId).isEmpty(),
        fixture.defaultTimeout(),
        "watchbox subscription registration");

    WatchboxAlertEvent event =
        new WatchboxAlertEvent(
            watchboxId,
            1L,
            UUID.randomUUID(),
            "object-1",
            UUID.randomUUID(),
            Instant.parse("2026-08-14T12:00:00Z"),
            WatchboxTransitionKind.ENTER,
            null,
            null);
    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(event));

    var delivered = awaitMessage(clientHandler, "watchbox alert delivery");
    assertThat(delivered.getDestination()).isEqualTo("/topic/watchbox/" + watchboxId);
    var json = readJson(delivered.getPayload().toByteArray());
    assertThat(json.get("watchboxId").asString()).isEqualTo(watchboxId.toString());
    assertThat(json.get("kind").asString()).isEqualTo("ENTER");
    assertThat(json.get("objectId").asString()).isEqualTo("object-1");
    assertThat(json.get("eventId").asString()).isEqualTo(event.eventId().toString());
  }

  @ParameterizedTest
  @EnumSource(
      value = LayerAccessScope.class,
      names = {"PRIVATE", "GLOBAL"})
  void subscribe_missingPrivateLayerAccess_deniesBeforeRegistrationAndReceivesNothing(
      LayerAccessScope readableScope) throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    UUID layerA = UUID.randomUUID();
    UUID layerB = UUID.randomUUID();
    seedReadableLayer(layerA, userId, readableScope);
    seedLayerWithoutAccess(layerB);
    UUID watchboxId = seedWatchbox(layerA, layerB);
    String destination = "/topic/watchbox/" + watchboxId;

    fixture.send(session, fixture.subscribe(destination));

    var rejection = awaitMessage(clientHandler, "watchbox subscribe denial");
    assertThat(rejection.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(rejection.getDestination()).isEmpty();
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(rejection.getHeadersMap()).containsEntry("operation", "SUBSCRIBE");
    assertThat(rejection.getHeadersMap()).containsEntry("rejected-destination", destination);
    assertThat(sessionRegistry.getDestinationSessions(destination)).isEmpty();
    assertThat(session.isOpen()).isTrue();

    WatchboxAlertEvent event =
        new WatchboxAlertEvent(
            watchboxId,
            1L,
            UUID.randomUUID(),
            "object-1",
            UUID.randomUUID(),
            Instant.parse("2026-08-14T12:00:00Z"),
            WatchboxTransitionKind.LEAVE,
            null,
            null);
    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(event));

    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void activeGlobalAdministratorMaySubscribeWithoutLayerMembership() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    seedCurrentAccount(userId, 1);
    jdbcTemplate.update("UPDATE opencop_user SET administrator = true WHERE id = ?", userId);
    UUID layerA = UUID.randomUUID();
    seedLayerWithoutAccess(layerA);
    UUID watchboxId = seedWatchbox(layerA);

    fixture.send(session, fixture.subscribe("/topic/watchbox/" + watchboxId));

    await(
        () -> !sessionRegistry.getDestinationSessions("/topic/watchbox/" + watchboxId).isEmpty(),
        fixture.defaultTimeout(),
        "administrator watchbox subscription registration");
  }

  @Test
  void unknownWatchboxIdIsDeniedEvenForAdministrators() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    seedCurrentAccount(userId, 1);
    jdbcTemplate.update("UPDATE opencop_user SET administrator = true WHERE id = ?", userId);
    String destination = "/topic/watchbox/" + UUID.randomUUID();

    fixture.send(session, fixture.subscribe(destination));

    var rejection = awaitMessage(clientHandler, "unknown watchbox subscribe denial");
    assertThat(rejection.getHeadersMap()).containsEntry("code", "SUBSCRIBE_DENIED");
    assertThat(sessionRegistry.getDestinationSessions(destination)).isEmpty();
  }

  @Test
  void malformedOrNonCanonicalWatchboxIdIsRejectedAsInvalidTopic() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(session, fixture.subscribe("/topic/watchbox/not-a-uuid"));
    var malformed = awaitMessage(clientHandler, "malformed watchbox id rejection");
    assertThat(malformed.getHeadersMap()).containsEntry("code", "INVALID_TOPIC");

    // UUID.fromString accepts this spelling, but the literal-string destination would never match
    // a broadcast, so the handler rejects non-canonical spellings outright.
    fixture.send(session, fixture.subscribe("/topic/watchbox/1-1-1-1-1"));
    var nonCanonical = awaitMessage(clientHandler, "non-canonical watchbox id rejection");
    assertThat(nonCanonical.getHeadersMap()).containsEntry("code", "INVALID_TOPIC");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void compositionDriftRevokesBeforeTheSinglePostExpiryTriggerCanDeliver() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    UUID layerA = UUID.randomUUID();
    seedLayerAccess(layerA, userId);
    UUID watchboxId = seedWatchbox(layerA);
    String destination = "/topic/watchbox/" + watchboxId;

    fixture.send(session, fixture.subscribe(destination));
    await(
        () -> !sessionRegistry.getDestinationSessions(destination).isEmpty(),
        fixture.defaultTimeout(),
        "watchbox subscription registration");

    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(alert(watchboxId)));
    var delivered = awaitMessage(clientHandler, "pre-revocation alert delivery");
    assertThat(delivered.getDestination()).isEqualTo(destination);

    UUID inaccessibleLayer = UUID.randomUUID();
    seedLayerWithoutAccess(inaccessibleLayer);
    jdbcTemplate.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        watchboxId,
        inaccessibleLayer);

    // The direct committed composition change is intentionally followed by one bounded wait and
    // one trigger. Repeated poll-publishes would weaken the no-leak assertion.
    Thread.sleep(1_100);
    WatchboxAlertEvent trigger = alert(watchboxId);
    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(trigger));

    // The first frame after the single trigger must be the revocation NACK. Do not predicate-poll
    // here: discarding an alert before a later NACK would hide the confidentiality failure.
    var revokedNack = awaitMessage(clientHandler, "SUBSCRIPTION_REVOKED notice");
    assertThat(revokedNack.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(revokedNack.getDestination()).isEmpty();
    assertThat(revokedNack.getHeadersMap())
        .containsEntry("code", "SUBSCRIPTION_REVOKED")
        .containsEntry("operation", "SUBSCRIBE");
    assertThat(revokedNack.getHeadersMap()).containsEntry("rejected-destination", destination);
    assertThat(session.isOpen()).isTrue();
    assertThat(sessionRegistry.getDestinationSessions(destination)).isEmpty();
    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();

    // The revocation cleared the session-side bookkeeping too: a re-SUBSCRIBE is re-evaluated
    // and denied properly instead of being swallowed by the already-subscribed check.
    fixture.send(session, fixture.subscribe(destination));
    var denied =
        awaitValue(
            () -> {
              try {
                return clientHandler.pollMessage(50, TimeUnit.MILLISECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted awaiting re-subscribe denial", e);
              }
            },
            message ->
                message != null && "SUBSCRIBE_DENIED".equals(message.getHeadersMap().get("code")),
            fixture.defaultTimeout(),
            "re-subscribe denial after revocation");
    assertThat(denied.getHeadersMap()).containsEntry("rejected-destination", destination);
  }

  @Test
  void revokedLayerAccessIsSweptAtTheNextDeliveryPastTheReauthInterval() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    UUID layerA = UUID.randomUUID();
    seedLayerAccess(layerA, userId);
    UUID watchboxId = seedWatchbox(layerA);
    String destination = "/topic/watchbox/" + watchboxId;

    fixture.send(session, fixture.subscribe(destination));
    await(
        () -> !sessionRegistry.getDestinationSessions(destination).isEmpty(),
        fixture.defaultTimeout(),
        "watchbox subscription registration");

    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(alert(watchboxId)));
    assertThat(awaitMessage(clientHandler, "pre-revocation alert delivery").getDestination())
        .isEqualTo(destination);

    jdbcTemplate.update(
        "DELETE FROM iam.layer_member WHERE layer_id = ? AND user_id = ?", layerA, userId);

    await(
        () -> {
          try {
            stringRedisTemplate.convertAndSend(
                CacheChannel.WATCHBOX_ALERTS.name(),
                objectMapper.writeValueAsString(alert(watchboxId)));
          } catch (Exception exception) {
            throw new AssertionError("alert publish failed", exception);
          }
          return sessionRegistry.getDestinationSessions(destination).isEmpty();
        },
        fixture.defaultTimeout(),
        "revocation sweep at the next delivery past the interval");

    var revokedNack =
        awaitValue(
            () -> {
              try {
                return clientHandler.pollMessage(50, TimeUnit.MILLISECONDS);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted awaiting role-revocation notice", exception);
              }
            },
            message ->
                message != null
                    && "SUBSCRIPTION_REVOKED".equals(message.getHeadersMap().get("code")),
            fixture.defaultTimeout(),
            "role-revocation notice");
    assertThat(revokedNack.getHeadersMap())
        .containsEntry("code", "SUBSCRIPTION_REVOKED")
        .containsEntry("rejected-destination", destination);
  }

  @Test
  void groupDerivedLayerAccessAdmitsAndReceivesWatchboxAlerts() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    UUID userId = awaitCurrentUserId();
    UUID layer = UUID.randomUUID();
    seedLayerAccessViaGroup(layer, userId, "MEMBER");
    UUID watchboxId = seedWatchbox(layer);
    String destination = "/topic/watchbox/" + watchboxId;

    fixture.send(session, fixture.subscribe(destination));
    await(
        () -> !sessionRegistry.getDestinationSessions(destination).isEmpty(),
        fixture.defaultTimeout(),
        "group-derived watchbox subscription registration");

    WatchboxAlertEvent event = alert(watchboxId);
    stringRedisTemplate.convertAndSend(
        CacheChannel.WATCHBOX_ALERTS.name(), objectMapper.writeValueAsString(event));

    var delivered = awaitMessage(clientHandler, "group-derived watchbox alert delivery");
    assertThat(delivered.getDestination()).isEqualTo(destination);
    assertThat(readJson(delivered.getPayload().toByteArray()).get("eventId").asString())
        .isEqualTo(event.eventId().toString());
  }

  private WatchboxAlertEvent alert(UUID watchboxId) {
    return new WatchboxAlertEvent(
        watchboxId,
        1L,
        UUID.randomUUID(),
        "object-1",
        UUID.randomUUID(),
        Instant.parse("2026-08-14T12:00:00Z"),
        WatchboxTransitionKind.ENTER,
        null,
        null);
  }

  private void seedReadableLayer(UUID layerId, UUID userId, LayerAccessScope scope) {
    switch (scope) {
      case PRIVATE -> seedLayerAccess(layerId, userId);
      case GLOBAL -> {
        seedLayerWithoutAccess(layerId);
        jdbcTemplate.update("UPDATE iam.layer SET access_scope = 'GLOBAL' WHERE id = ?", layerId);
      }
    }
  }

  private void seedLayerWithoutAccess(UUID layerId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer(id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
  }

  private UUID seedWatchbox(UUID... layerIds) {
    UUID watchboxId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox (id, name, direction, polygon_json)
        VALUES (?, ?, 'BOTH', '{"type":"Polygon","coordinates":[]}'::jsonb)
        """,
        watchboxId,
        "watchbox-" + watchboxId);
    for (UUID layerId : layerIds) {
      jdbcTemplate.update(
          "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
          watchboxId,
          layerId);
    }
    return watchboxId;
  }
}
