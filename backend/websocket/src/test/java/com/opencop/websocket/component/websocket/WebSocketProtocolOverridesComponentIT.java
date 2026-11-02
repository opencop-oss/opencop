// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.websocket.component.support.AbstractWebSocketComponentIT;
import com.opencop.websocket.component.support.WebSocketComponentTestApplication;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Import(WebSocketProtocolOverridesComponentIT.ClockConfig.class)
@SpringBootTest(
    classes = WebSocketComponentTestApplication.class,
    properties = {
      "websocket.max-inbound-message-size=2KB",
      "websocket.protocol-violation-limit=2",
      "websocket.protocol-violation-window=1s"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketProtocolOverridesComponentIT extends AbstractWebSocketComponentIT {

  private static final int MAX_INBOUND_MESSAGE_BYTES = 2_048;
  private static final Instant BASE_TIME = Instant.parse("2030-08-14T12:00:00Z");

  @Autowired private MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(BASE_TIME);
  }

  @Test
  void configuredLimitClosesTheSecondViolationWithoutClosingAHealthySibling() throws Exception {
    var violatorHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession violator = fixture.connect(websocketUrl(), violatorHandler);
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthy = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two registered sessions");

    fixture.send(violator, fixture.subscribe("/invalid/first"));

    assertProtocolError(
        awaitMessage(violatorHandler, "first configured-limit violation"),
        Map.of(
            "code", "INVALID_DESTINATION",
            "operation", "SUBSCRIBE",
            "rejected-destination", "/invalid/first"),
        "Invalid destination");
    assertThat(violator.isOpen()).isTrue();

    fixture.send(violator, fixture.subscribe("/invalid/second"));

    var closeStatus = awaitCloseStatus(violatorHandler, "configured protocol-limit close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    assertThat(closeStatus.getReason()).isEqualTo("Protocol limit exceeded");
    assertThat(violatorHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "configured-limit socket cleanup");
    assertThat(healthy.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();
  }

  @Test
  void repeatedDeniedSubscribesReachTheConfiguredLimitAndCloseOnlyThatSocket() throws Exception {
    var violatorHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession violator = fixture.connect(websocketUrl(), violatorHandler);
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthy = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two registered sessions");
    UUID deniedId = UUID.randomUUID();

    fixture.send(violator, fixture.subscribe(deniedId));

    assertProtocolError(
        awaitMessage(violatorHandler, "first denied subscribe"),
        Map.of(
            "code", "SUBSCRIBE_DENIED",
            "operation", "SUBSCRIBE",
            "rejected-destination", "/topic/queue/event/" + deniedId),
        "Insufficient access");
    assertThat(violator.isOpen()).isTrue();

    fixture.send(violator, fixture.subscribe(deniedId));

    var closeStatus = awaitCloseStatus(violatorHandler, "denied-subscribe threshold close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    assertThat(closeStatus.getReason()).isEqualTo("Protocol limit exceeded");
    assertThat(violatorHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "denied-subscribe socket cleanup");
    assertThat(sessionRegistry.getDestinationSessions("/topic/queue/event/" + deniedId)).isEmpty();
    assertThat(healthy.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();
  }

  @Test
  void deniedSubscribeAndInvalidDestinationShareTheConfiguredBudget() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();
    UUID deniedId = UUID.randomUUID();

    fixture.send(session, fixture.subscribe(deniedId));

    assertProtocolError(
        awaitMessage(clientHandler, "denied subscribe drawing down the shared budget"),
        Map.of(
            "code", "SUBSCRIBE_DENIED",
            "operation", "SUBSCRIBE",
            "rejected-destination", "/topic/queue/event/" + deniedId),
        "Insufficient access");
    assertThat(session.isOpen()).isTrue();

    fixture.send(session, fixture.subscribe("/invalid/after-denial"));

    var closeStatus = awaitCloseStatus(clientHandler, "shared-budget threshold close");
    assertThat(closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
    assertThat(closeStatus.getReason()).isEqualTo("Protocol limit exceeded");
    assertThat(clientHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
  }

  @Test
  void configuredWindowExpiresTheFirstViolationForANewSession() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    awaitSingleUserSession();

    fixture.send(session, fixture.subscribe("/invalid/before-window"));
    assertProtocolError(
        awaitMessage(clientHandler, "violation before configured window expires"),
        Map.of(
            "code", "INVALID_DESTINATION",
            "operation", "SUBSCRIBE",
            "rejected-destination", "/invalid/before-window"),
        "Invalid destination");

    clock.advance(Duration.ofSeconds(1));
    fixture.send(session, fixture.subscribe("/invalid/after-window"));

    assertProtocolError(
        awaitMessage(clientHandler, "violation after configured window expires"),
        Map.of(
            "code", "INVALID_DESTINATION",
            "operation", "SUBSCRIBE",
            "rejected-destination", "/invalid/after-window"),
        "Invalid destination");
    assertThat(session.isOpen()).isTrue();
    assertThat(clientHandler.closeStatus()).isNull();
  }

  @Test
  void configuredTextBoundaryReachesApplicationHandling() throws Exception {
    var clientHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession session = fixture.connect(websocketUrl(), clientHandler);
    var userSession = awaitSingleUserSession();

    assertThat(userSession.getWebsocketSession().getTextMessageSizeLimit())
        .isEqualTo(MAX_INBOUND_MESSAGE_BYTES);
    assertThat(userSession.getWebsocketSession().getBinaryMessageSizeLimit())
        .isEqualTo(MAX_INBOUND_MESSAGE_BYTES);

    fixture.sendText(session, "x".repeat(MAX_INBOUND_MESSAGE_BYTES));

    assertProtocolError(
        awaitMessage(clientHandler, "configured-boundary text rejection"),
        Map.of("code", "UNSUPPORTED_MESSAGE_TYPE", "operation", "TEXT"),
        "Text messages not supported");
    assertThat(session.isOpen()).isTrue();
  }

  @Test
  void configuredBoundaryAppliesToUnfragmentedBinaryMessages() throws Exception {
    var boundaryHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession boundarySession = fixture.connect(websocketUrl(), boundaryHandler);
    awaitSingleUserSession();

    fixture.send(boundarySession, new byte[MAX_INBOUND_MESSAGE_BYTES]);

    var boundaryClose = awaitCloseStatus(boundaryHandler, "configured-boundary binary close");
    assertThat(boundaryClose.getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    awaitNoRegisteredSessions();

    var oversizedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession oversizedSession = fixture.connect(websocketUrl(), oversizedHandler);
    var healthyHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession healthySession = fixture.connect(websocketUrl(), healthyHandler);
    await(
        () -> sessionRegistry.getUsersSessions().size() == 2,
        fixture.defaultTimeout(),
        "two sessions before configured oversized binary message");

    fixture.send(oversizedSession, new byte[MAX_INBOUND_MESSAGE_BYTES + 1]);

    var oversizedClose = awaitCloseStatus(oversizedHandler, "configured oversized binary close");
    assertThat(oversizedClose.getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    assertThat(oversizedHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "configured oversized binary socket cleanup");
    assertThat(healthySession.isOpen()).isTrue();
    assertThat(healthyHandler.closeStatus()).isNull();
  }

  @Test
  void configuredBoundaryAppliesToReassembledFragmentedBinaryMessages() throws Exception {
    int firstFragmentBytes = MAX_INBOUND_MESSAGE_BYTES / 2;
    var boundaryHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession boundarySession = fixture.connect(websocketUrl(), boundaryHandler);
    awaitSingleUserSession();

    fixture.send(boundarySession, new byte[firstFragmentBytes], false);
    fixture.send(boundarySession, new byte[MAX_INBOUND_MESSAGE_BYTES - firstFragmentBytes], true);

    var boundaryClose =
        awaitCloseStatus(boundaryHandler, "configured fragmented-boundary binary close");
    assertThat(boundaryClose.getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    awaitNoRegisteredSessions();

    var oversizedHandler = new RecordingBinaryWebSocketHandler();
    WebSocketSession oversizedSession = fixture.connect(websocketUrl(), oversizedHandler);
    awaitSingleUserSession();

    fixture.send(oversizedSession, new byte[firstFragmentBytes], false);
    fixture.send(
        oversizedSession, new byte[MAX_INBOUND_MESSAGE_BYTES - firstFragmentBytes + 1], true);

    var oversizedClose =
        awaitCloseStatus(oversizedHandler, "configured fragmented oversized binary close");
    assertThat(oversizedClose.getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    assertThat(oversizedHandler.pollMessage(300, TimeUnit.MILLISECONDS)).isNull();
    awaitNoRegisteredSessions();
  }

  private static void assertProtocolError(
      SocketMessage error, Map<String, String> expectedHeaders, String expectedReason) {
    assertThat(error.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(error.getDestination()).isEmpty();
    assertThat(error.getCompressed()).isFalse();
    assertThat(error.getHeadersMap()).containsExactlyInAnyOrderEntriesOf(expectedHeaders);
    assertThat(error.getPayload().toStringUtf8()).isEqualTo(expectedReason);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {

    @Bean
    @Primary
    MutableClock protocolClock() {
      return new MutableClock(BASE_TIME);
    }
  }

  static final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    MutableClock(Instant initialInstant) {
      this(initialInstant, ZoneOffset.UTC);
    }

    private MutableClock(Instant initialInstant, ZoneId zone) {
      this.now = new AtomicReference<>(initialInstant);
      this.zone = zone;
    }

    void set(Instant instant) {
      now.set(instant);
    }

    void advance(Duration duration) {
      now.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return zone.equals(newZone) ? this : new MutableClock(now.get(), newZone);
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
