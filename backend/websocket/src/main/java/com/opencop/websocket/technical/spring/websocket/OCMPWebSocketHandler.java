// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.opencop.core.sdk.helper.UuidHelper;
import com.opencop.websocket.ports.business.usecase.event.LiveEventAccessAuthorizer;
import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import com.opencop.websocket.technical.gateway.websocket.WebSocketEventListener;
import com.opencop.websocket.technical.spring.security.interceptor.SessionSecurityContextInterceptor;
import com.opencop.websocket.technical.spring.websocket.ClientMessageValidator.TopicDestination;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.SessionLimitExceededException;

@Component
public class OCMPWebSocketHandler extends AbstractWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(OCMPWebSocketHandler.class);
  private static final String PROTOCOL_LIMIT_CLOSE_REASON = "Protocol limit exceeded";

  @Value("${opencop.dev.mode}")
  private boolean devMode;

  private static final String REJECTION_COUNTER_NAME = "opencop.websocket.rejections";
  private static final String TAG_CODE = "code";

  private final WsSessionRegistry registry;
  private final LiveEventAccessAuthorizer liveEventAccessAuthorizer;
  private final WatchboxSubscribeAuthorizer watchboxSubscribeAuthorizer;
  private final WebSocketEventListener listener;
  private final SessionExpiryKiller sessionExpiryKiller; // for heartbeat
  private final MeterRegistry meterRegistry;
  private final ClientMessageValidator clientMessageValidator;
  private final ProtocolErrorMessageFactory protocolErrorMessageFactory;
  private final Clock clock;

  public OCMPWebSocketHandler(
      WsSessionRegistry registry,
      LiveEventAccessAuthorizer liveEventAccessAuthorizer,
      WatchboxSubscribeAuthorizer watchboxSubscribeAuthorizer,
      WebSocketEventListener listener,
      SessionExpiryKiller sessionExpiryKiller,
      MeterRegistry meterRegistry,
      ClientMessageValidator clientMessageValidator,
      ProtocolErrorMessageFactory protocolErrorMessageFactory,
      Clock clock) {
    this.registry = registry;
    this.liveEventAccessAuthorizer = liveEventAccessAuthorizer;
    this.watchboxSubscribeAuthorizer = watchboxSubscribeAuthorizer;
    this.listener = listener;
    this.sessionExpiryKiller = sessionExpiryKiller;
    this.meterRegistry = meterRegistry;
    this.clientMessageValidator = clientMessageValidator;
    this.protocolErrorMessageFactory = protocolErrorMessageFactory;
    this.clock = clock;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    var principal = session.getPrincipal();
    if (principal == null) {
      // fallback
      Object p = session.getAttributes().get(SessionSecurityContextInterceptor.ATTR_PRINCIPAL);
      if (p instanceof java.security.Principal alt) {
        principal = alt;
      }
    }

    if (principal == null) {
      log.info("Rejecting WS session {}: no authenticated principal", session.getId());
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
      return;
    }

    log.info(
        "Websocket session {} established for user {}",
        session.getId(),
        UserSessionUtils.getUserIdFromSession(session));

    var registration = registry.register(session);
    var userSession = registration.session();
    if (!registration.registered()) {
      sessionExpiryKiller.closeAndCleanup(
          userSession, new CloseStatus(SESSION_ENDED, "Session ended"));
      return;
    }
    try {
      sessionExpiryKiller.scheduleClose(
          userSession, Instant.now().plusMillis(20_000), 0, SessionExpiryKiller.HEARTBEAT);
      sessionExpiryKiller.scheduleClose(
          userSession, userSession.getAuthenticationDeadline(), 0, SessionExpiryKiller.AUTH);
    } catch (RuntimeException e) {
      sessionExpiryKiller.closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
      throw e;
    }
  }

  @Override
  protected void handleBinaryMessage(@NonNull WebSocketSession session, BinaryMessage message) {
    try {
      SocketMessage socketMessage = SocketMessage.parseFrom(message.getPayload());

      UserSession userSession = registeredUserSession(session, socketMessage.getType().name());
      if (userSession == null) {
        return;
      }

      var validation = clientMessageValidator.validate(socketMessage);
      if (validation instanceof ClientMessageValidator.Rejected rejected) {
        handleProtocolViolation(userSession, rejected.rejection());
        return;
      }
      var accepted = (ClientMessageValidator.Accepted) validation;

      switch (socketMessage.getType()) {
        case SocketMessage.Type.SUBSCRIBE ->
            handleSubscribe(
                userSession, socketMessage.getDestination(), accepted.topicDestination());
        case SocketMessage.Type.UNSUBSCRIBE ->
            handleUnsubscribe(userSession, socketMessage.getDestination());
        case SocketMessage.Type.HEARTBEAT -> handleHeartbeat(userSession);
        default -> throw new IllegalStateException("Validator accepted an unsupported frame type");
      }
    } catch (InvalidProtocolBufferException _) {
      log.info("BinaryMessage was not a SocketMessage; closing session {}.", session.getId());
      closeSessionQuietly(session, CloseStatus.BAD_DATA);
    }
  }

  @Override
  protected void handleTextMessage(
      @NonNull WebSocketSession session, @NonNull TextMessage message) {
    UserSession userSession = registeredUserSession(session, "TEXT");
    if (userSession == null) {
      return;
    }

    handleProtocolViolation(
        userSession,
        new ClientMessageValidator.Rejection(
            ClientMessageValidator.ErrorCode.UNSUPPORTED_MESSAGE_TYPE,
            ClientMessageValidator.Operation.TEXT,
            null));
  }

  private @Nullable UserSession registeredUserSession(WebSocketSession session, String frameType) {
    if (session.getPrincipal() == null) {
      closeSessionQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("Unauthenticated"));
      return null;
    }

    UserSession userSession = registry.getUserSession(session);
    if (userSession == null) {
      logUnregisteredSessionFrame(session, frameType);
      closeSessionQuietly(session, CloseStatus.SERVER_ERROR);
    }
    return userSession;
  }

  private void logUnregisteredSessionFrame(WebSocketSession session, String frameType) {
    if (!session.isOpen()) {
      log.debug(
          "Received {} frame for closed unregistered session {}.", frameType, session.getId());
      return;
    }
    log.warn(
        "Received {} frame for unregistered session {}. Closing session.",
        frameType,
        session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.error(
        "Transport error on session {} for user {} ({})",
        session.getId(),
        UserSessionUtils.getUserIdFromSession(session),
        exception.getClass().getSimpleName(),
        exception);
    UserSession userSession = registry.getUserSession(session);
    if (userSession != null) {
      sessionExpiryKiller.closeAndCleanup(userSession, CloseStatus.SERVER_ERROR);
      return;
    }
    closeSessionQuietly(session, CloseStatus.SERVER_ERROR);
  }

  @Override
  public void afterConnectionClosed(
      @NonNull WebSocketSession session, @NonNull CloseStatus status) {
    UserSession userSession = registry.getUserSession(session);
    if (userSession != null) {
      sessionExpiryKiller.closeAndCleanup(userSession, status);
    }
    log.info(
        "Session {} for user {} closed with CloseStatus {} and removed from registry.",
        session.getId(),
        UserSessionUtils.getUserIdFromSession(session),
        status);

    if (status.equalsCode(CloseStatus.NORMAL)) {
      log.info("Session closed gracefully.");
    } else if (status.equalsCode(CloseStatus.NOT_ACCEPTABLE)) {
      log.info("Client may not have sent a CONNECT frame.");
    } else if (status.getCode() == AUTH_FAIL) {
      log.info("Session failed to authenticate.");
    }
  }

  private void handleSubscribe(
      UserSession session, String destination, @Nullable TopicDestination topicDestination) {
    // If already subscribed, do nothing
    if (session.isSubscribed(destination)) {
      log.debug("User is already subscribed to topic {}", destination);
      return;
    }

    if (topicDestination != null) {
      if (!authorizeTopicSubscribe(session, destination, topicDestination)) {
        return;
      }
      // Registered only AFTER authorization passes, and atomically with the session-side
      // bookkeeping: the registry holds one monitor across both writes (the mirror of revoke),
      // so a revocation sweep can never interleave between them and strand a session-side entry
      // that the already-subscribed check above would then silently honor forever.
      if (!registry.subscribe(destination, session)) {
        return;
      }
    } else if (!session.addDestination(destination)) {
      // User queues have no destination index entry; the session's own bookkeeping suffices.
      return;
    }

    if (!session.acquireSubscriptionCallbackPermit()) {
      return;
    }
    listener.onSubscribe(destination, session.getUserId());
    log.info(
        "Session {} for user {} accepted subscription to {}",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        destination);
  }

  /**
   * The validator is the deny-by-default topic-family allowlist; this second typed dispatch keeps
   * each admitted family on its own authorization policy. A {@code false} return means the attempt
   * was already rejected toward the client.
   */
  private boolean authorizeTopicSubscribe(
      UserSession session, String destination, TopicDestination topicDestination) {
    return switch (topicDestination.family()) {
      case LIVE_EVENT ->
          authorizeLiveEventTopicSubscribe(session, destination, topicDestination.id());
      case WATCHBOX -> authorizeWatchboxTopicSubscribe(session, destination, topicDestination.id());
    };
  }

  private boolean authorizeLiveEventTopicSubscribe(
      UserSession session, String destination, UUID destinationId) {
    // opencop.dev.mode is false in the production application.yml and true only in the
    // local/docker-local profiles; the bypass admits only the same-char fixture UUIDs the local
    // fixture tooling subscribes with. This bypass is why the family methods stay separate
    // instead of sharing one authorize shape: it is load-bearing for local development here and
    // deliberately absent for watchboxes.
    boolean allowed =
        (devMode && UuidHelper.isSameCharUuid(destinationId))
            || liveEventAccessAuthorizer.userCanSubscribe(session.getUserId(), destinationId);

    if (!allowed) {
      rejectDeniedSubscribe(session, destination);
      return false;
    }
    return true;
  }

  private boolean authorizeWatchboxTopicSubscribe(
      UserSession session, String destination, UUID watchboxId) {
    // Deliberately no dev-mode bypass: watchbox subscriptions are fail-closed everywhere, so a
    // fixture UUID grants nothing here (the live-event branch documents why its bypass exists).
    if (!watchboxSubscribeAuthorizer.userCanSubscribe(session.getUserId(), watchboxId)) {
      rejectDeniedSubscribe(session, destination);
      return false;
    }
    return true;
  }

  private void rejectDeniedSubscribe(UserSession session, String destination) {
    log.warn(
        "Rejecting subscribe for session {} user {} because access to {} was denied",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        destination);
    // Reject only this subscription attempt; keep session alive
    handleProtocolViolation(
        session,
        new ClientMessageValidator.Rejection(
            ClientMessageValidator.ErrorCode.SUBSCRIBE_DENIED,
            ClientMessageValidator.Operation.SUBSCRIBE,
            destination));
  }

  private void handleUnsubscribe(UserSession session, String destination) {
    session.removeDestination(destination);
    registry.removeFromDestination(destination, session);
    log.info(
        "Session {} for user {} processed unsubscribe from {}",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        destination);
  }

  private void handleHeartbeat(UserSession session) {
    log.debug(
        "Received heartbeat from session {} for user {}",
        session.getWebsocketSession().getId(),
        session.getUserId());
    sessionExpiryKiller.scheduleClose(
        session, Instant.now().plusMillis(25_000), 0, SessionExpiryKiller.HEARTBEAT);
  }

  private void handleProtocolViolation(
      UserSession session, ClientMessageValidator.Rejection rejection) {
    // Count every rejection decision, even when the nack can no longer be delivered. The tag
    // vocabulary is bounded by ClientMessageValidator.ErrorCode.
    meterRegistry
        .counter(REJECTION_COUNTER_NAME, TAG_CODE, rejection.errorCode().name())
        .increment();
    var result = session.getProtocolViolationTracker().record(clock.instant());
    switch (result.decision()) {
      case BELOW_THRESHOLD -> {
        logSubThresholdViolation(session, rejection, result.count());
        sendRejection(session, rejection);
      }
      case THRESHOLD_REACHED -> {
        log.warn(
            "Protocol violation threshold reached for session {} user {} operation={} code={} count={}",
            session.getWebsocketSession().getId(),
            session.getUserId(),
            rejection.operation(),
            rejection.errorCode(),
            result.count());
        sessionExpiryKiller.closeAndCleanup(
            session,
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), PROTOCOL_LIMIT_CLOSE_REASON));
      }
      case ALREADY_REACHED -> {
        // A concurrent callback already made the authoritative threshold decision.
      }
    }
  }

  private void logSubThresholdViolation(
      UserSession session, ClientMessageValidator.Rejection rejection, int count) {
    if (count == 1) {
      log.info(
          "Protocol violation for session {} user {} operation={} code={} count={}",
          session.getWebsocketSession().getId(),
          session.getUserId(),
          rejection.operation(),
          rejection.errorCode(),
          count);
      return;
    }
    log.debug(
        "Protocol violation for session {} user {} operation={} code={} count={}",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        rejection.operation(),
        rejection.errorCode(),
        count);
  }

  private void sendRejection(UserSession session, ClientMessageValidator.Rejection rejection) {
    var ws = session.getWebsocketSession();
    if (ws == null || !ws.isOpen()) {
      return;
    }

    var nack = protocolErrorMessageFactory.create(rejection);
    try {
      ws.sendMessage(new BinaryMessage(nack.toByteArray()));
    } catch (SessionLimitExceededException e) {
      log.warn(
          "Failed to send rejection message to session {} because send limits were exceeded",
          ws.getId(),
          e);
      sessionExpiryKiller.closeAndCleanup(session, CloseStatus.SESSION_NOT_RELIABLE);
    } catch (IOException e) {
      log.warn("Failed to send rejection message to session {}", ws.getId(), e);
      sessionExpiryKiller.closeAndCleanup(session, CloseStatus.SERVER_ERROR);
    }
  }

  private void closeSessionQuietly(WebSocketSession session, CloseStatus status) {
    try {
      if (session != null && session.isOpen()) {
        session.close(status);
      }
    } catch (IOException _) {
      // Do nothing
    }
  }
}
