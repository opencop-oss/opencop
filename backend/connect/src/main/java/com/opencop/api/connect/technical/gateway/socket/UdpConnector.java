// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UDP connector that listens on a port for incoming datagrams.
 *
 * <p>Unlike TCP which is a stream, UDP provides discrete packets, so each non-empty datagram is
 * delivered whole as one message.
 */
public final class UdpConnector implements SocketConnector, Runnable {

  private static final Logger log = LoggerFactory.getLogger(UdpConnector.class);

  private final UUID sourceId;
  private final int port;
  private final int receiveBufferSize;

  private final AtomicReference<ConnectorPhase> phase =
      new AtomicReference<>(new ConnectorPhase.New());
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();

  public UdpConnector(UUID sourceId, int port) {
    this(sourceId, port, 65535); // Max UDP packet size default
  }

  public UdpConnector(UUID sourceId, int port, int receiveBufferSize) {
    this.sourceId = sourceId;
    this.port = port;
    this.receiveBufferSize = receiveBufferSize;
  }

  @Override
  public void run() {
    log.info("Starting UDP listener for {} on port {}", sourceId, port);

    try (DatagramSocket socket = new DatagramSocket(port)) {
      socket.setSoTimeout(1000); // 1s timeout to allow checking phase and interruption

      Session session = createSession(socket);

      ConnectorPhase current = phase.get();
      if (!(current instanceof ConnectorPhase.Connecting currentConnecting)) {
        log.warn("Connector {} in invalid state {} for transition to Running", sourceId, current);
        return;
      }

      if (phase.compareAndSet(
          current, new ConnectorPhase.Running(currentConnecting.thread(), session))) {
        notifyState(ConnectorState.CONNECTED);
        readLoop(socket);
      }
    } catch (SocketException e) {
      log.error("Failed to open UDP socket on port {}", port, e);
      handleError(e);
    } catch (InterruptedException e) {
      log.info("UDP listener for {} interrupted", sourceId);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("Unexpected error in UDP listener for {}", sourceId, e);
      handleError(e);
    } finally {
      cleanup();
    }
  }

  private void readLoop(DatagramSocket socket) throws InterruptedException {
    byte[] receiveBuffer = new byte[receiveBufferSize];
    log.info("Connection {} entering UDP readLoop", sourceId);

    while (phase.get() instanceof ConnectorPhase.Running) {
      if (Thread.currentThread().isInterrupted()) {
        break;
      }

      DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
      try {
        socket.receive(packet);

        if (packet.getLength() > 0) {
          byte[] data = new byte[packet.getLength()];
          System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
          notifyMessage(data);
        }
      } catch (SocketTimeoutException e) {
        // Expected, continue
      } catch (IOException e) {
        if (phase.get() instanceof ConnectorPhase.Running) {
          log.error("Error receiving UDP packet for {}", sourceId, e);
        }
        break;
      }
    }
    log.info("Connection {} exited UDP readLoop", sourceId);
  }

  private Session createSession(DatagramSocket socket) {
    return new Session() {
      @Override
      public java.io.InputStream input() {
        throw new UnsupportedOperationException("UDP session does not support stream input");
      }

      @Override
      public java.io.OutputStream output() {
        throw new UnsupportedOperationException("UDP session does not support stream output");
      }

      @Override
      public void close() {
        socket.close();
      }
    };
  }

  private void handleError(Throwable t) {
    ConnectorPhase current = phase.get();
    if (current instanceof ConnectorPhase.Stopped) {
      return;
    }
    if (phase.compareAndSet(current, new ConnectorPhase.Failed(t))) {
      notifyError(t);
    }
  }

  private void cleanup() {
    ConnectorPhase current = phase.get();
    if (current instanceof ConnectorPhase.Running) {
      phase.compareAndSet(current, new ConnectorPhase.Disconnected());
      notifyState(ConnectorState.DISCONNECTED);
    } else if (current instanceof ConnectorPhase.Stopped) {
      notifyState(ConnectorState.STOPPED);
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
    Thread t = new Thread(this, "UdpConnector-" + sourceId);
    t.setDaemon(true);

    if (phase.get() instanceof ConnectorPhase.New current) {
      if (phase.compareAndSet(current, new ConnectorPhase.Connecting(t))) {
        t.start();
      }
    }
  }

  @Override
  public boolean stop() {
    while (true) {
      ConnectorPhase current = phase.get();
      if (current instanceof ConnectorPhase.Running(Thread thread, Session session)) {
        if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
          session.close();
          thread.interrupt();
          return true;
        }
        Thread.onSpinWait();
      } else if (current instanceof ConnectorPhase.Connecting(Thread thread)) {
        if (phase.compareAndSet(current, new ConnectorPhase.Stopped())) {
          thread.interrupt();
          return true;
        }
        Thread.onSpinWait();
      } else {
        return false;
      }
    }
  }

  @Override
  public void addListener(ConnectorListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ConnectorListener listener) {
    listeners.remove(listener);
  }

  private void notifyState(ConnectorState state) {
    for (var listener : listeners) {
      try {
        listener.onStateChange(sourceId, state);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onStateChange", listener.getClass().getName(), e);
      }
    }
  }

  private void notifyMessage(byte[] message) {
    for (var listener : listeners) {
      try {
        listener.onMessage(sourceId, message);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onMessage", listener.getClass().getName(), e);
      }
    }
  }

  private void notifyError(Throwable t) {
    for (var listener : listeners) {
      try {
        listener.onError(sourceId, t);
      } catch (Exception e) {
        log.error("Listener {} threw exception in onError", listener.getClass().getName(), e);
      }
    }
    notifyState(ConnectorState.FAILED);
  }
}
