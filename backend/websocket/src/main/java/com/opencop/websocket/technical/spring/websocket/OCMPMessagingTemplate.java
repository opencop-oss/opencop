// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.google.protobuf.ByteString;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.websocket.ports.gateway.websocket.SendAttemptResult;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.SessionLimitExceededException;

public class OCMPMessagingTemplate {
  private static final Logger log = LoggerFactory.getLogger(OCMPMessagingTemplate.class);
  private final WsSessionRegistry registry;
  private final SessionExpiryKiller sessionExpiryKiller;
  private final ExecutorService sessionCleanupExecutor;

  public OCMPMessagingTemplate(
      WsSessionRegistry registry, SessionExpiryKiller sessionExpiryKiller) {
    this.registry = registry;
    this.sessionExpiryKiller = sessionExpiryKiller;
    this.sessionCleanupExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable);
              thread.setName("ws-cleanup-" + thread.getId());
              thread.setDaemon(true);
              return thread;
            });
  }

  @PreDestroy
  public void shutdownSessionCleanupExecutor() {
    sessionCleanupExecutor.shutdownNow();
  }

  /** For test visibility only — checks whether the cleanup executor has been shut down. */
  boolean isSessionCleanupExecutorShutdown() {
    return sessionCleanupExecutor.isShutdown();
  }

  public boolean sendToUserSession(
      UserSession session,
      String destination,
      byte[] payload,
      Map<String, String> headers,
      boolean compress) {
    try {
      if (session.getWebsocketSession().isOpen() && session.isSubscribed(destination)) {
        session
            .getWebsocketSession()
            .sendMessage(
                new BinaryMessage(
                    SocketMessage.newBuilder()
                        .setType(SocketMessage.Type.SEND)
                        .setPayload(ByteString.copyFrom(compress ? compress(payload) : payload))
                        .setDestination(destination)
                        .putAllHeaders(headers == null ? new HashMap<>() : headers)
                        .setCompressed(compress)
                        .build()
                        .toByteArray()));
        return true;
      }
    } catch (SessionLimitExceededException e) {
      log.warn(
          "Session {} exceeded send limits ({}), removing it from the registry",
          session.getWebsocketSession().getId(),
          e.getMessage());
      cleanupSession(session, "send limits exceeded", e.getStatus());
    } catch (IOException e) {
      log.error("Failed to send message to session {}", session.getWebsocketSession().getId(), e);
      cleanupSession(session, "send failure", CloseStatus.SERVER_ERROR);
    }

    return false;
  }

  public void sendToUser(UUID userId, String destination, byte[] payload, boolean compress) {
    sendToUser(userId, destination, payload, null, compress);
  }

  /**
   * Sends a frame to all local sessions registered for the given user that have an active
   * subscription to the destination queue. Returns {@code true} if at least one such session
   * existed and received the frame; {@code false} when the user has no local session or none of
   * their sessions are subscribed to the destination — meaning the frame was not delivered.
   */
  public boolean sendToUser(
      UUID userId,
      String destination,
      byte[] payload,
      Map<String, String> headers,
      boolean compress) {
    final Set<UserSession> userSessions = registry.getUserSessions(userId);

    if (userSessions.isEmpty()) {
      log.info(
          "No active websocket session found for user {} at destination {}", userId, destination);
      return false;
    }

    boolean anyDelivered = false;
    for (UserSession userSession : userSessions) {
      if (sendToUserSession(userSession, destination, payload, headers, compress)) {
        anyDelivered = true;
      }
    }
    return anyDelivered;
  }

  /**
   * Sends a binary frame to the session and returns a future that resolves when the underlying
   * Jakarta transport confirms write completion. Cleanup of failed sessions runs off the transport
   * selector thread onto the dedicated cleanup thread pool.
   */
  public CompletableFuture<Void> sendToSessionAwait(
      UserSession session,
      String destination,
      byte[] payload,
      Map<String, String> headers,
      boolean compress) {
    byte[] frameBytes =
        SocketMessage.newBuilder()
            .setType(SocketMessage.Type.SEND)
            .setPayload(ByteString.copyFrom(compress ? compress(payload) : payload))
            .setDestination(destination)
            .putAllHeaders(headers == null ? new HashMap<>() : headers)
            .setCompressed(compress)
            .build()
            .toByteArray();

    WebSocketSession websocketSession = session.getWebsocketSession();
    if (!(websocketSession instanceof AckingWebSocketSessionDecorator acking)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "Expected AckingWebSocketSessionDecorator for awaited websocket send"));
    }
    CompletableFuture<Void> sendFuture = acking.sendBinaryAwait(frameBytes);

    return sendFuture.whenCompleteAsync(
        (ignored, ex) -> {
          if (ex != null) {
            log.error(
                "Async send failed for session {}", session.getWebsocketSession().getId(), ex);
            cleanupSession(session, "async send failure", CloseStatus.SERVER_ERROR);
          }
        },
        sessionCleanupExecutor);
  }

  /**
   * Sends to all local subscribed sessions for the user and awaits all writes. Resolves to
   * NO_SESSION when no subscribed session is found; to DELIVERED when at least one write succeeds;
   * completes exceptionally only when every write failed.
   */
  public CompletableFuture<SendAttemptResult> sendToUserAwait(
      UUID userId,
      String destination,
      byte[] payload,
      Map<String, String> headers,
      boolean compress) {
    Set<UserSession> userSessions = registry.getUserSessions(userId);

    List<CompletableFuture<Void>> perSessionFutures = new ArrayList<>();
    for (UserSession session : userSessions) {
      if (session.isSubscribed(destination)) {
        perSessionFutures.add(sendToSessionAwait(session, destination, payload, headers, compress));
      }
    }

    if (perSessionFutures.isEmpty()) {
      log.info("No subscribed session found for user {} at destination {}", userId, destination);
      return CompletableFuture.completedFuture(SendAttemptResult.NO_SESSION);
    }

    AtomicBoolean anySucceeded = new AtomicBoolean(false);
    List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();

    List<CompletableFuture<Void>> trackedFutures = new ArrayList<>();
    for (CompletableFuture<Void> f : perSessionFutures) {
      trackedFutures.add(
          f.handle(
              (v, ex) -> {
                if (ex == null) {
                  anySucceeded.set(true);
                } else {
                  failures.add(ex);
                }
                return null;
              }));
    }

    @SuppressWarnings("unchecked")
    CompletableFuture<Void>[] trackedArray = trackedFutures.toArray(new CompletableFuture[0]);

    return CompletableFuture.allOf(trackedArray)
        .handle(
            (ignored, allOfEx) -> {
              if (anySucceeded.get()) {
                return SendAttemptResult.DELIVERED;
              }
              // All sessions failed — complete exceptionally with first root cause
              Throwable firstCause =
                  failures.isEmpty()
                      ? new IOException("all sessions failed")
                      : rootCause(failures.get(0));
              CompletableFuture<SendAttemptResult> failed = new CompletableFuture<>();
              failed.completeExceptionally(firstCause);
              return failed;
            })
        .thenCompose(
            resultOrFuture -> {
              if (resultOrFuture instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<SendAttemptResult> f =
                    (CompletableFuture<SendAttemptResult>) resultOrFuture;
                return f;
              }
              return CompletableFuture.completedFuture((SendAttemptResult) resultOrFuture);
            });
  }

  private static Throwable rootCause(Throwable t) {
    Throwable cause = t.getCause();
    return (cause != null) ? cause : t;
  }

  public void broadcast(String topic, byte[] payload, boolean compress) {
    broadcast(topic, payload, null, compress);
  }

  public void broadcast(
      String destination, byte[] payload, Map<String, String> headers, boolean compress) {
    final Set<UserSession> sessions = registry.getDestinationSessions(destination);

    if (!sessions.isEmpty()) {
      for (UserSession userSession : sessions) {
        sendToUserSession(userSession, destination, payload, headers, compress);
      }
      return;
    }
    log.info("Broadcast to {} skipped because there are no subscribed sessions", destination);
  }

  private byte[] compress(byte[] data) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
      gzip.write(data);
    } catch (IOException e) {
      throw new GlobalOpenCopException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return outputStream.toByteArray();
  }

  private void cleanupSession(UserSession session, String reason, CloseStatus closeStatus) {
    log.warn(
        "Cleaning up websocket session {} for user {} after {}",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        reason);
    sessionExpiryKiller.closeAndCleanup(session, closeStatus);
  }
}
