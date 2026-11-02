// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketSession;

public class WsSessionRegistry {
  private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);

  private final Object lifecycleMonitor = new Object();
  private final @Nullable SessionRepository<? extends Session> sessionRepository;
  private final UserSessionFactory userSessionFactory;
  private final ConcurrentMap<UUID, Set<UserSession>> userSessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Set<UserSession>> destinationSessions =
      new ConcurrentHashMap<>();

  public WsSessionRegistry(
      @Nullable SessionRepository<? extends Session> sessionRepository,
      UserSessionFactory userSessionFactory) {
    this.sessionRepository = sessionRepository;
    this.userSessionFactory = userSessionFactory;
  }

  public Registration register(WebSocketSession wsSession) {
    UserSession userSession = userSessionFactory.create(wsSession);
    if (!wsSession.isOpen()) {
      return new Registration(userSession, false);
    }
    if (userSession.isSessionless()) {
      return registerOpenSession(userSession);
    }
    if (!backingSessionIsActive(userSession)) {
      return new Registration(userSession, false);
    }
    return registerOpenSession(userSession);
  }

  /**
   * Client SUBSCRIBE registration: writes the destination index and the session's own bookkeeping
   * under one monitor hold — the mirror of {@link #revoke}, which clears both. Split writes were
   * strandable: a revocation sweep interleaving between them would clear the index entry and then
   * lose to the late session-side write, leaving a session that believes it is subscribed on a
   * destination that no longer delivers to it — and the handler's already-subscribed fast path
   * would then swallow every later re-SUBSCRIBE. A closing or unregistered session gets neither
   * write.
   *
   * @return whether the session was registered (false when it is already closing/unregistered)
   */
  public boolean subscribe(String destination, UserSession session) {
    synchronized (lifecycleMonitor) {
      if (session.isClosing()
          || !userSessions.getOrDefault(session.getUserId(), Set.of()).contains(session)) {
        return false;
      }
      if (!session.addDestination(destination)) {
        return false;
      }
      destinationSessions
          .computeIfAbsent(destination, _ -> ConcurrentHashMap.newKeySet())
          .add(session);
    }
    log.info(
        "Session {} for user {} subscribed to {}. destinationSubscriberCount={}",
        session.getWebsocketSession().getId(),
        session.getUserId(),
        destination,
        destinationSessions.getOrDefault(destination, Set.of()).size());
    return true;
  }

  public void removeFromDestination(String destination, UserSession session) {
    synchronized (lifecycleMonitor) {
      destinationSessions.computeIfPresent(
          destination,
          (_, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
          });
      log.info(
          "Session {} for user {} unsubscribed from {}. destinationSubscriberCount={}",
          session.getWebsocketSession().getId(),
          session.getUserId(),
          destination,
          destinationSessions.getOrDefault(destination, Set.of()).size());
    }
  }

  /**
   * Server-initiated removal of a subscription the client did not ask to end. Unlike {@link
   * #removeFromDestination} (whose UNSUBSCRIBE caller clears the session's own bookkeeping at the
   * handler), this clears BOTH the destination index and {@link UserSession#removeDestination} —
   * leaving the session-side entry behind would make a later re-SUBSCRIBE silently no-op against
   * the already-subscribed check instead of being re-evaluated.
   *
   * @return whether the session was actually registered on the destination
   */
  public boolean revoke(String destination, UserSession session) {
    synchronized (lifecycleMonitor) {
      var sessions = destinationSessions.get(destination);
      var removed = sessions != null && sessions.remove(session);
      if (sessions != null && sessions.isEmpty()) {
        destinationSessions.remove(destination, sessions);
      }
      session.removeDestination(destination);
      if (removed) {
        log.info(
            "Session {} for user {} revoked from {}. destinationSubscriberCount={}",
            session.getWebsocketSession().getId(),
            session.getUserId(),
            destination,
            destinationSessions.getOrDefault(destination, Set.of()).size());
      }
      return removed;
    }
  }

  public void remove(WebSocketSession wsSession) {
    synchronized (lifecycleMonitor) {
      UUID userId = UserSessionUtils.getUserIdFromSession(wsSession);
      if (userId == null) {
        return;
      }
      String wsSessionId = wsSession.getId();
      Set<UserSession> removedSessions = new HashSet<>();

      userSessions.computeIfPresent(
          userId,
          (_, sessions) -> {
            sessions.removeIf(
                s -> {
                  boolean isMatch = s.getWebsocketSession().getId().equals(wsSessionId);
                  if (isMatch) {
                    removedSessions.add(s);
                  }
                  return isMatch;
                });
            return sessions.isEmpty() ? null : sessions;
          });

      if (!removedSessions.isEmpty()) {
        removeFromSecondaryIndexes(removedSessions);
        log.info(
            "Removed websocket session {} for user {}. removedSessionCount={} remainingUserSessionCount={} totalSessionCount={}",
            wsSessionId,
            userId,
            removedSessions.size(),
            userSessions.getOrDefault(userId, Set.of()).size(),
            getUsersSessions().size());
      }
    }
  }

  public void remove(UUID userId) {
    synchronized (lifecycleMonitor) {
      Set<UserSession> removedSessions = userSessions.remove(userId);
      if (removedSessions != null) {
        removeFromSecondaryIndexes(removedSessions);
        log.info(
            "Removed all websocket sessions for user {}. removedSessionCount={} totalSessionCount={}",
            userId,
            removedSessions.size(),
            getUsersSessions().size());
      }
    }
  }

  public void remove(UserSession session) {
    synchronized (lifecycleMonitor) {
      userSessions.computeIfPresent(
          session.getUserId(),
          (_, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
          });
      destinationSessions.values().forEach(set -> set.remove(session));
      pruneEmptyDestinationEntries();
      log.info(
          "Removed websocket session {} for user {}. remainingUserSessionCount={} totalSessionCount={}",
          session.getWebsocketSession().getId(),
          session.getUserId(),
          userSessions.getOrDefault(session.getUserId(), Set.of()).size(),
          getUsersSessions().size());
    }
  }

  public List<UserSession> getUsersSessions() {
    return userSessions.values().stream().flatMap(Set::stream).toList();
  }

  public UserSession getUserSession(WebSocketSession wsSession) {
    synchronized (lifecycleMonitor) {
      UUID userId = UserSessionUtils.getUserIdFromSession(wsSession);
      if (userId == null) {
        return null;
      }

      Set<UserSession> sessions = userSessions.get(userId);
      if (sessions != null) {
        for (UserSession session : sessions) {
          if (session.getWebsocketSession().getId().equals(wsSession.getId())) {
            return session;
          }
        }
      }
      return null;
    }
  }

  public Set<UserSession> getUserSessions(UUID userId) {
    if (userId == null) {
      return Set.of();
    }
    return userSessions.getOrDefault(userId, Set.of());
  }

  public Set<UserSession> getDestinationSessions(String destination) {
    if (destination == null) {
      return Collections.emptySet();
    }
    return destinationSessions.getOrDefault(destination, Collections.emptySet());
  }

  public record Registration(UserSession session, boolean registered) {}

  private Registration registerOpenSession(UserSession userSession) {
    synchronized (lifecycleMonitor) {
      WebSocketSession wsSession = userSession.getWebsocketSession();
      UUID userId = userSession.getUserId();
      if (!wsSession.isOpen() || userSession.isClosing()) {
        return new Registration(userSession, false);
      }
      userSessions.computeIfAbsent(userId, _ -> ConcurrentHashMap.newKeySet()).add(userSession);
      log.info(
          "Registered websocket session {} for user {}. userSessionCount={} totalSessionCount={}",
          wsSession.getId(),
          userId,
          userSessions.getOrDefault(userId, Set.of()).size(),
          getUsersSessions().size());
      return new Registration(userSession, true);
    }
  }

  private void removeFromSecondaryIndexes(Set<UserSession> removedSessions) {
    removedSessions.forEach(
        session -> destinationSessions.values().forEach(set -> set.remove(session)));
    pruneEmptyDestinationEntries();
  }

  private void pruneEmptyDestinationEntries() {
    destinationSessions.forEach(
        (destination, sessions) -> {
          if (sessions.isEmpty()) {
            destinationSessions.remove(destination, sessions);
          }
        });
  }

  private boolean backingSessionIsActive(UserSession userSession) {
    return sessionRepository != null
        && sessionRepository.findById(userSession.getBackingSessionId()) != null;
  }
}
