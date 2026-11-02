// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a compiled behavior plan while preserving the raw SocketConnector interface.
 *
 * <p>The plan can be swapped while the connection runs: the behaviors change, the raw socket does
 * not. Every runner of the current plan is held together so a swap replaces them as one.
 */
final class ConnectionBehaviorSocketConnector implements SocketConnector, ConnectorListener {

  private static final Logger log =
      LoggerFactory.getLogger(ConnectionBehaviorSocketConnector.class);

  /** The runners executing one plan, compiled from the effective settings they hold. */
  private record Runners(
      Object effectiveSettings,
      OnConnectRunner onConnect,
      List<RecurringBehaviorRunner> recurring,
      ReceiveIdleHealthRunner health) {

    void start() {
      recurring.forEach(RecurringBehaviorRunner::start);
      health.start();
    }

    void cancel() {
      onConnect.cancel();
      recurring.forEach(RecurringBehaviorRunner::cancel);
      health.cancel();
    }

    /**
     * Stops a plan that a swap replaced while the socket stays open. Sends already in flight
     * finish, since interrupting them could close the socket.
     */
    void retire() {
      onConnect.cancel(false);
      recurring.forEach(runner -> runner.cancel(false));
      health.cancel(false);
    }
  }

  private final SocketConnector raw;
  private final UUID sourceId;
  private final ScheduledThreadPoolExecutor scheduler;
  private final ExecutorService behaviorExecutor;
  private final Clock clock;
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();
  private final Object lifecycleLock = new Object();

  private volatile Runners runners;
  // Set from the connect event, not read from the raw socket, so a swap and that event never both
  // run the same on-connect actions.
  private boolean connected;
  private boolean closed;
  private ConnectorState terminalState;

  ConnectionBehaviorSocketConnector(
      SocketConnector raw,
      UUID sourceId,
      ConnectionBehaviorPlan plan,
      Object effectiveSettings,
      ScheduledThreadPoolExecutor scheduler,
      ExecutorService behaviorExecutor,
      Clock clock) {
    this.raw = Objects.requireNonNull(raw, "raw");
    this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.behaviorExecutor = Objects.requireNonNull(behaviorExecutor, "behaviorExecutor");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.runners = runners(plan, effectiveSettings);
    raw.addListener(this);
  }

  /** Returns the effective settings the current plan was compiled from. */
  Object effectiveSettings() {
    return runners.effectiveSettings();
  }

  /**
   * Replaces the running plan with one compiled from new effective settings, keeping the raw socket
   * open. Recurring delays and the receive-idle window restart from the swap. When the connection
   * is up, the new on-connect actions start at once in the background, so a changed presence is
   * announced without waiting for the next connect; this returns without waiting for their sends.
   *
   * @return false when the connector has already closed, in which case nothing changes
   */
  boolean applyPlan(ConnectionBehaviorPlan plan, Object effectiveSettings) {
    var replacement = runners(plan, effectiveSettings);
    Runners replaced;
    boolean announce;
    synchronized (lifecycleLock) {
      if (closed) {
        return false;
      }
      replaced = runners;
      runners = replacement;
      replaced.retire();
      announce = connected;
      if (announce) {
        replacement.start();
      }
    }
    // The caller is the reconciler, so the announcement runs in the background: a slow peer must
    // not stall reconciliation, and a fatal behavior closes this connector without escaping.
    if (announce) {
      replacement.onConnect().runInBackground();
    }
    return true;
  }

  @Override
  public UUID id() {
    return raw.id();
  }

  @Override
  public ConnectorState state() {
    synchronized (lifecycleLock) {
      if (terminalState != null) {
        return terminalState;
      }
    }
    return raw.state();
  }

  @Override
  public void start() {
    raw.start();
  }

  @Override
  public boolean stop() {
    cancelBehaviorWork();
    return raw.stop();
  }

  @Override
  public void send(byte[] data) throws Exception {
    raw.send(data);
  }

  @Override
  public void addListener(ConnectorListener listener) {
    listeners.add(Objects.requireNonNull(listener, "listener"));
  }

  @Override
  public void removeListener(ConnectorListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void onStateChange(UUID eventSourceId, ConnectorState state) {
    if (state == ConnectorState.CONNECTED) {
      notifyStateChange(eventSourceId, state);
      Runners current;
      synchronized (lifecycleLock) {
        connected = true;
        current = runners;
        current.start();
      }
      // A swap landing after this block cancels these runners and announces its own plan.
      current.onConnect().run();
      return;
    }

    boolean cancel = false;
    if (isTerminal(state)) {
      synchronized (lifecycleLock) {
        if (terminalState != null) {
          return;
        }
        terminalState = state;
        if (!closed) {
          closed = true;
          cancel = true;
        }
      }
    }
    if (cancel) {
      cancelRunners();
    }
    notifyStateChange(eventSourceId, state);
  }

  @Override
  public void onMessage(UUID eventSourceId, byte[] data) {
    runners.health().recordReceiveActivity();
    notifyListeners(listener -> listener.onMessage(eventSourceId, data), "onMessage");
  }

  @Override
  public void onError(UUID eventSourceId, Throwable error) {
    notifyListeners(listener -> listener.onError(eventSourceId, error), "onError");
  }

  @Override
  public void onRestartRequested(UUID eventSourceId, Throwable reason) {
    notifyListeners(
        listener -> listener.onRestartRequested(eventSourceId, reason), "onRestartRequested");
  }

  private void cancelBehaviorWork() {
    synchronized (lifecycleLock) {
      closed = true;
    }
    cancelRunners();
  }

  private void cancelRunners() {
    runners.cancel();
  }

  private Runners runners(ConnectionBehaviorPlan plan, Object effectiveSettings) {
    Objects.requireNonNull(plan, "plan");
    Objects.requireNonNull(effectiveSettings, "effectiveSettings");
    var messageExecutor = new MessageActionExecutor(raw, sourceId, clock);
    var taskExecutor = new BehaviorTaskExecutor(behaviorExecutor, this::failBehavior);
    return new Runners(
        effectiveSettings,
        new OnConnectRunner(
            plan.onConnect(), behaviorExecutor, messageExecutor, this::failBehavior),
        plan.recurring().stream()
            .map(
                action ->
                    new RecurringBehaviorRunner(action, scheduler, taskExecutor, messageExecutor))
            .toList(),
        new ReceiveIdleHealthRunner(
            plan.idleMessage(),
            plan.reconnectAfterIdle(),
            scheduler,
            taskExecutor,
            messageExecutor,
            clock,
            this::reconnectAfterIdle));
  }

  private void failBehavior(String behaviorName, Error error) {
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      terminalState = ConnectorState.FAILED;
    }
    cancelRunners();

    try {
      raw.stop();
    } catch (RuntimeException | Error stopFailure) {
      if (stopFailure != error) {
        error.addSuppressed(stopFailure);
      }
      log.error(
          "Connection {} could not close after fatal behavior {}",
          sourceId,
          behaviorName,
          stopFailure);
    }
    log.error("Connection {} behavior {} failed fatally", sourceId, behaviorName, error);
    notifyListeners(listener -> listener.onError(sourceId, error), "onError");
    notifyStateChange(sourceId, ConnectorState.FAILED);
  }

  private void reconnectAfterIdle(SocketTimeoutException reason) {
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      terminalState = ConnectorState.DISCONNECTED;
    }
    cancelRunners();

    Throwable stopFailure = null;
    try {
      raw.stop();
    } catch (RuntimeException | Error failure) {
      stopFailure = failure;
    }
    notifyStateChange(sourceId, ConnectorState.DISCONNECTED);
    notifyListeners(
        listener -> listener.onRestartRequested(sourceId, reason), "onRestartRequested");
    if (stopFailure != null) {
      var failure = stopFailure;
      log.error("Connection {} raw close failed during receive-idle reconnect", sourceId, failure);
      notifyListeners(listener -> listener.onError(sourceId, failure), "onError");
      BehaviorTaskExecutor.rethrowFailure(failure);
    }
  }

  private void notifyStateChange(UUID eventSourceId, ConnectorState state) {
    notifyListeners(listener -> listener.onStateChange(eventSourceId, state), "onStateChange");
  }

  private void notifyListeners(Consumer<ConnectorListener> notification, String callback) {
    for (var listener : listeners) {
      try {
        notification.accept(listener);
      } catch (Exception exception) {
        log.error(
            "Connector listener {} failed in {}",
            listener.getClass().getName(),
            callback,
            exception);
      }
    }
  }

  private static boolean isTerminal(ConnectorState state) {
    return switch (state) {
      case DISCONNECTED, STOPPED, INCOMPATIBLE, FAILED, UNKNOWN -> true;
      case NEW, CONNECTING, CONNECTED -> false;
    };
  }
}
