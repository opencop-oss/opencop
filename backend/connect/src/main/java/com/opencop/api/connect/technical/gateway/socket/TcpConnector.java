// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.ports.gateway.socket.framing.FramingStrategy;
import com.opencop.api.connect.ports.gateway.socket.framing.MessageTooLargeDroppedException;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw TCP connector with thread-safe lifecycle management.
 *
 * <p>Opens one session, reads framed messages, serializes writes, publishes transport state, and
 * closes. Reconnection and integration behavior belong to higher-level modules.
 */
public class TcpConnector implements SocketConnector, Runnable {

  private static final Logger log = LoggerFactory.getLogger(TcpConnector.class);

  private final UUID sourceId;
  private final SessionOpener sessionOpener;
  private final FramingStrategy framing;
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean stoppedNotificationPublished = new AtomicBoolean();
  private final AtomicReference<ConnectorPhase> phase =
      new AtomicReference<>(new ConnectorPhase.New());

  public TcpConnector(UUID sourceId, FramingStrategy framingStrategy, SessionOpener sessionOpener) {
    this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
    this.framing = Objects.requireNonNull(framingStrategy, "framingStrategy");
    this.sessionOpener = Objects.requireNonNull(sessionOpener, "sessionOpener");
  }

  @Override
  public void run() {
    long connectStartTime = System.currentTimeMillis();
    Thread currentThread = Thread.currentThread();
    if (!ownsConnectingPhase(currentThread)) {
      log.warn("Connection {} run() called in unexpected phase: {}", sourceId, phase.get());
      return;
    }

    notifyState(ConnectorState.CONNECTING);
    try {
      runSession(currentThread, connectStartTime);
    } catch (Throwable failure) {
      handleFailure(failure);
      if (failure instanceof Error error) {
        throw error;
      }
    } finally {
      publishTerminalState();
    }
  }

  private boolean ownsConnectingPhase(Thread currentThread) {
    return phase.get() instanceof ConnectorPhase.Connecting(Thread owner) && owner == currentThread;
  }

  private void runSession(Thread currentThread, long connectStartTime) throws Exception {
    try (Session session = sessionOpener.open()) {
      if (!enterRunning(currentThread, session)) {
        log.info("Connection {} stopped during connect", sourceId);
        return;
      }

      long connectDuration = System.currentTimeMillis() - connectStartTime;
      log.info("Connection {} reached CONNECTED state after {}ms", sourceId, connectDuration);
      notifyState(ConnectorState.CONNECTED);
      readLoop(session.input());
      transitionAfterReadLoop();
    }
  }

  private boolean enterRunning(Thread currentThread, Session session) {
    while (true) {
      var current = phase.get();
      if (!(current instanceof ConnectorPhase.Connecting)) {
        return false;
      }
      if (phase.compareAndSet(current, new ConnectorPhase.Running(currentThread, session))) {
        return true;
      }
      Thread.onSpinWait();
    }
  }

  private void transitionAfterReadLoop() {
    while (true) {
      var current = phase.get();
      if (current instanceof ConnectorPhase.Running) {
        if (phase.compareAndSet(current, new ConnectorPhase.Disconnected())) {
          log.info("Connection {} server closed connection (EOF)", sourceId);
          return;
        }
        Thread.onSpinWait();
      } else {
        return;
      }
    }
  }

  private void publishTerminalState() {
    switch (phase.get()) {
      case ConnectorPhase.Stopped() -> notifyStopped();
      case ConnectorPhase.Disconnected() -> notifyState(ConnectorState.DISCONNECTED);
      default -> {
        // Failure publication occurs in handleFailure; other phases are non-terminal.
      }
    }
  }

  private void handleFailure(Throwable failure) {
    if (failure instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    while (true) {
      var current = phase.get();
      if (current instanceof ConnectorPhase.Stopped) {
        log.info("Connection {} closed by stop()", sourceId);
        notifyStopped();
        return;
      }
      if (!(current instanceof ConnectorPhase.Running)
          && !(current instanceof ConnectorPhase.Connecting)) {
        return;
      }
      if (phase.compareAndSet(current, new ConnectorPhase.Failed(failure))) {
        log.error("Connection {} failed: {}", sourceId, failure.getMessage(), failure);
        notifyError(failure);
        return;
      }
      Thread.onSpinWait();
    }
  }

  private void readLoop(InputStream input) throws IOException {
    log.info("Connection {} entering readLoop", sourceId);
    try {
      while (phase.get() instanceof ConnectorPhase.Running) {
        if (Thread.currentThread().isInterrupted()) {
          log.info("Connection {} thread interrupted, stopping read loop", sourceId);
          return;
        }
        try {
          byte[] message = framing.readMessage(input);
          if (message == null) {
            log.info("Connection {} server closed connection (EOF)", sourceId);
            return;
          }
          notifyMessage(message);
        } catch (SocketTimeoutException _) {
          // Transport wake-up only; receive-idle integration behavior lives in the decorator.
        } catch (MessageTooLargeDroppedException exception) {
          log.warn("Connection {} skipped oversized message: {}", sourceId, exception.getMessage());
        }
      }
    } finally {
      log.info("Connection {} exited readLoop", sourceId);
    }
  }

  private void notifyMessage(byte[] message) {
    log.trace(
        "Connection {} successfully read message, notifying {} listeners",
        sourceId,
        listeners.size());
    for (var listener : listeners) {
      try {
        listener.onMessage(sourceId, message);
      } catch (Exception exception) {
        log.error(
            "Listener {} threw exception in onMessage", listener.getClass().getName(), exception);
      }
    }
  }

  @Override
  public UUID id() {
    return sourceId;
  }

  @Override
  public ConnectorState state() {
    return switch (phase.get()) {
      case ConnectorPhase.New() -> ConnectorState.NEW;
      case ConnectorPhase.Connecting(_) -> ConnectorState.CONNECTING;
      case ConnectorPhase.Running(_, _) -> ConnectorState.CONNECTED;
      case ConnectorPhase.Stopped() -> ConnectorState.STOPPED;
      case ConnectorPhase.Disconnected() -> ConnectorState.DISCONNECTED;
      case ConnectorPhase.Incompatible(_) -> ConnectorState.INCOMPATIBLE;
      case ConnectorPhase.Failed(_) -> ConnectorState.FAILED;
    };
  }

  @Override
  public void start() {
    Thread thread = new Thread(this, "TcpConnector-" + sourceId);
    thread.setDaemon(true);
    if (phase.get() instanceof ConnectorPhase.New current
        && phase.compareAndSet(current, new ConnectorPhase.Connecting(thread))) {
      thread.start();
    }
  }

  @Override
  public boolean stop() {
    while (true) {
      var current = phase.get();
      switch (current) {
        case ConnectorPhase.Running(Thread thread, Session session) -> {
          if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
            try {
              session.close();
            } finally {
              thread.interrupt();
              notifyStopped();
            }
            return true;
          }
          Thread.onSpinWait();
        }
        case ConnectorPhase.Connecting(Thread thread) -> {
          if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
            thread.interrupt();
            notifyStopped();
            return true;
          }
          Thread.onSpinWait();
        }
        default -> {
          return false;
        }
      }
    }
  }

  @Override
  public void send(byte[] data) throws IOException {
    var current = phase.get();
    if (!(current instanceof ConnectorPhase.Running(_, Session session))) {
      throw new IllegalStateException(
          "Cannot send: connector not in RUNNING state (current: "
              + current.getClass().getSimpleName()
              + ")");
    }
    var output = session.output();
    synchronized (output) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedIOException("Socket write was cancelled");
      }
      output.write(data);
      output.flush();
    }
  }

  @Override
  public void addListener(ConnectorListener listener) {
    listeners.add(Objects.requireNonNull(listener, "listener"));
  }

  @Override
  public void removeListener(ConnectorListener listener) {
    listeners.remove(listener);
  }

  private void notifyState(ConnectorState state) {
    for (var listener : listeners) {
      try {
        listener.onStateChange(sourceId, state);
      } catch (Exception exception) {
        log.error(
            "Listener {} threw exception in onStateChange",
            listener.getClass().getName(),
            exception);
      }
    }
  }

  private void notifyStopped() {
    if (stoppedNotificationPublished.compareAndSet(false, true)) {
      notifyState(ConnectorState.STOPPED);
    }
  }

  private void notifyError(Throwable error) {
    for (var listener : listeners) {
      try {
        listener.onError(sourceId, error);
      } catch (Exception exception) {
        log.error(
            "Listener {} threw exception in onError", listener.getClass().getName(), exception);
      }
    }
    notifyState(ConnectorState.FAILED);
  }
}
