// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.HEARTBEAT_EXPIRED;
import static com.opencop.websocket.technical.spring.security.AuthConstants.SESSION_ENDED;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Service
public class SessionExpiryKiller {

  private static final Logger log = LoggerFactory.getLogger(SessionExpiryKiller.class);

  private final TaskScheduler authScheduler;
  private final TaskScheduler heartbeatScheduler;
  private final WsSessionRegistry registry;
  // Protects task-map mutation only; scheduler, future, socket, and registry calls run outside it.
  private final Object taskLifecycleMonitor = new Object();
  private final ConcurrentMap<String, ConcurrentMap<Integer, ScheduledTask>> tasks =
      new ConcurrentHashMap<>();
  public static final int HEARTBEAT = 0;
  public static final int AUTH = 1;

  public SessionExpiryKiller(
      @Qualifier("wsAuthExpiryScheduler") TaskScheduler authScheduler,
      @Qualifier("wsHeartbeatExpiryScheduler") TaskScheduler heartbeatScheduler,
      WsSessionRegistry registry) {
    this.authScheduler = authScheduler;
    this.heartbeatScheduler = heartbeatScheduler;
    this.registry = registry;
  }

  public void scheduleClose(UserSession session, Instant exp, long graceSeconds, int scheduler) {
    String sessionId = getSessionId(session);
    if (sessionId == null) {
      return;
    }

    final int status = scheduler == AUTH ? SESSION_ENDED : HEARTBEAT_EXPIRED;
    long delayMs = Duration.between(Instant.now(), exp.plusSeconds(graceSeconds)).toMillis();
    if (delayMs <= 0) {
      closeNow(session, status);
      return;
    }

    var scheduledTask = new ScheduledTask();
    ScheduledTask replacedTask;
    synchronized (taskLifecycleMonitor) {
      if (session.isClosing()) {
        return;
      }
      replacedTask =
          tasks
              .computeIfAbsent(sessionId, _ -> new ConcurrentHashMap<>())
              .put(scheduler, scheduledTask);
    }
    if (replacedTask != null) {
      replacedTask.cancel();
    }
    try {
      var scheduledFuture =
          (scheduler == AUTH ? authScheduler : heartbeatScheduler)
              .schedule(() -> closeNow(session, status), Instant.now().plusMillis(delayMs));
      scheduledTask.attach(scheduledFuture);
    } catch (RuntimeException e) {
      remove(sessionId, scheduler, scheduledTask);
      throw e;
    }
  }

  public void cancel(UserSession session) {
    String sessionId = getSessionId(session);
    if (sessionId == null) {
      return;
    }

    ConcurrentMap<Integer, ScheduledTask> scheduledByType;
    synchronized (taskLifecycleMonitor) {
      scheduledByType = tasks.remove(sessionId);
    }
    if (scheduledByType != null) {
      scheduledByType.values().forEach(ScheduledTask::cancel);
    }
  }

  private void remove(String sessionId, int scheduler, ScheduledTask scheduledTask) {
    synchronized (taskLifecycleMonitor) {
      var scheduledByType = tasks.get(sessionId);
      if (scheduledByType == null) {
        return;
      }
      scheduledByType.remove(scheduler, scheduledTask);
      if (scheduledByType.isEmpty()) {
        tasks.remove(sessionId, scheduledByType);
      }
    }
  }

  private void closeNow(UserSession session, int status) {
    closeAndCleanup(session, new CloseStatus(status, closeReason(status)));
  }

  public void closeAndCleanup(UserSession session, CloseStatus status) {
    if (session == null || !session.beginClosing()) {
      return;
    }
    WebSocketSession websocketSession = session.getWebsocketSession();
    try {
      if (websocketSession != null && websocketSession.isOpen()) {
        websocketSession.close(status);
      }
    } catch (Exception e) {
      log.warn(
          "Failed to close websocket session {} with status {} ({})",
          websocketSession == null ? "unknown" : websocketSession.getId(),
          status.getCode(),
          e.getClass().getSimpleName());
    } finally {
      session.clearProtocolState();
      cancel(session);
      registry.remove(session);
    }
  }

  private static String closeReason(int status) {
    return status == SESSION_ENDED ? "Session ended" : "Heartbeat expired";
  }

  private static String getSessionId(UserSession session) {
    if (session == null || session.getWebsocketSession() == null) {
      return null;
    }
    return session.getWebsocketSession().getId();
  }

  private static final class ScheduledTask {
    private ScheduledFuture<?> future;
    private boolean cancelled;

    synchronized void attach(ScheduledFuture<?> scheduledFuture) {
      if (scheduledFuture == null) {
        return;
      }
      if (cancelled) {
        scheduledFuture.cancel(false);
      } else {
        future = scheduledFuture;
      }
    }

    synchronized void cancel() {
      cancelled = true;
      if (future != null) {
        future.cancel(false);
      }
    }
  }
}
