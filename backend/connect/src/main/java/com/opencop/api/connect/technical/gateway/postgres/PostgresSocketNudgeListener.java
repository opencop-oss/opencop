// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.postgres;

import com.opencop.api.connect.business.reconciliation.DesiredOwnershipReconciler;
import com.opencop.core.sdk.model.nudge.SocketReconciliationNudge;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.ObjectMapper;

/** Dedicated Postgres LISTEN loop for best-effort socket reconciliation nudges. */
public class PostgresSocketNudgeListener implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PostgresSocketNudgeListener.class);
  private static final int NOTIFICATION_POLL_TIMEOUT_MS = 1_000;
  private static final long RECONNECT_BACKOFF_MS = 1_000L;
  private static final long EARLY_RECONCILE_MIN_INTERVAL_MS = 1_000L;
  private static final int LIFECYCLE_PHASE = Integer.MAX_VALUE - 100;

  private final PostgresNudgeListenerConnectionSource connectionSource;
  private final ObjectMapper objectMapper;
  private final DesiredOwnershipReconciler reconciler;
  private final Duration shutdownJoinTimeout;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private volatile Thread listenerThread;
  private volatile Connection currentConnection;
  private long lastEarlyReconcileAtMillis = 0L;

  public PostgresSocketNudgeListener(
      PostgresNudgeListenerConnectionSource connectionSource,
      ObjectMapper objectMapper,
      DesiredOwnershipReconciler reconciler,
      Duration shutdownJoinTimeout) {
    this.connectionSource = connectionSource;
    this.objectMapper = objectMapper;
    this.reconciler = reconciler;
    this.shutdownJoinTimeout =
        shutdownJoinTimeout == null || shutdownJoinTimeout.isNegative()
            ? Duration.ofSeconds(5)
            : shutdownJoinTimeout;
  }

  @Override
  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    var thread = new Thread(this::listenLoop, "PostgresSocketNudgeListener");
    thread.setDaemon(true);
    listenerThread = thread;
    thread.start();
  }

  @Override
  public void stop() {
    running.set(false);
    closeCurrentConnection();
    var thread = listenerThread;
    if (thread != null) {
      thread.interrupt();
    }
  }

  @Override
  public void stop(Runnable callback) {
    try {
      stop();
      joinListenerThread();
    } finally {
      callback.run();
    }
  }

  @Override
  public boolean isRunning() {
    return running.get();
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return LIFECYCLE_PHASE;
  }

  private void listenLoop() {
    try {
      while (running.get()) {
        try (var connection = connectionSource.getConnection()) {
          currentConnection = connection;
          registerListenAndReconcile(connection);
          var pgConnection = connection.unwrap(PGConnection.class);
          while (running.get()) {
            // this call blocks so we're not burning CPU busy-looping
            var notifications = pgConnection.getNotifications(NOTIFICATION_POLL_TIMEOUT_MS);
            if (notifications == null) {
              continue;
            }
            for (var notification : notifications) {
              handlePayload(notification.getParameter());
            }
          }
        } catch (SQLException e) {
          if (running.get()) {
            log.warn(
                "Postgres socket nudge listener disconnected; reconnecting: {}", e.getMessage(), e);
            sleepBeforeReconnect();
          }
        } catch (Exception e) {
          if (running.get()) {
            log.warn("Postgres socket nudge listener failed; reconnecting: {}", e.getMessage(), e);
            sleepBeforeReconnect();
          }
        } finally {
          currentConnection = null;
        }
      }
    } finally {
      // defensive - under normal operation there is exactly one of these running for the life of
      // the app
      currentConnection = null;
      if (Thread.currentThread() == listenerThread) {
        listenerThread = null;
      }
    }
  }

  void registerListenAndReconcile(Connection connection) throws SQLException {
    // JPA note: LISTEN needs this dedicated connection and PGConnection-backed polling loop.
    try (var statement = connection.createStatement()) {
      statement.execute("LISTEN " + SocketReconciliationNudge.CHANNEL);
    }
    log.info("Listening for Postgres socket nudges on {}", SocketReconciliationNudge.CHANNEL);
    reconciler.reconcileOnce();
  }

  void handlePayload(String payload) {
    SocketReconciliationNudge nudge;
    try {
      nudge = objectMapper.readValue(payload, SocketReconciliationNudge.class);
    } catch (Exception e) {
      log.warn("Ignoring malformed socket reconciliation nudge payload: {}", payload, e);
      return;
    }

    if (nudge.version() != SocketReconciliationNudge.CURRENT_VERSION) {
      log.warn("Ignoring socket reconciliation nudge with unsupported version {}", nudge.version());
      return;
    }

    requestEarlyReconciliation();
  }

  private void requestEarlyReconciliation() {
    if (!running.get()) {
      return;
    }

    var now = System.currentTimeMillis();
    var elapsed = now - lastEarlyReconcileAtMillis;

    if (elapsed < EARLY_RECONCILE_MIN_INTERVAL_MS) {
      log.debug("Coalescing socket reconciliation nudge; last early reconcile {}ms ago", elapsed);
      return;
    }

    lastEarlyReconcileAtMillis = now;

    try {
      // PG NOTIFY is only a wake-up; reconciliation rereads authoritative desired state.
      log.info("Received socket reconciliation nudge; running early reconciliation");
      reconciler.reconcileOnce();
    } catch (Exception e) {
      log.warn(
          "Early desired ownership reconciliation failed after socket reconciliation nudge: {}",
          e.getMessage(),
          e);
    }
  }

  private void closeCurrentConnection() {
    var connection = currentConnection;
    if (connection == null) {
      return;
    }
    try {
      connection.close();
    } catch (SQLException e) {
      log.debug("Failed to close Postgres socket nudge listener connection", e);
    }
  }

  private void joinListenerThread() {
    var thread = listenerThread;
    if (thread == null || thread == Thread.currentThread()) {
      return;
    }
    try {
      thread.join(Math.max(1L, shutdownJoinTimeout.toMillis()));
      if (thread.isAlive()) {
        log.warn("Postgres socket nudge listener did not stop within {}", shutdownJoinTimeout);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for Postgres socket nudge listener to stop");
    }
  }

  private void sleepBeforeReconnect() {
    try {
      Thread.sleep(RECONNECT_BACKOFF_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
