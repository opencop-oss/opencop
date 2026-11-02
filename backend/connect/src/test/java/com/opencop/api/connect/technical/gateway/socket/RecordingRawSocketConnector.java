// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.testing.integration.connection.behavior.LifecycleEventRecorder;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Connect-local recording adapter for exercising behavior through the SocketConnector seam. */
final class RecordingRawSocketConnector implements SocketConnector {

  private final UUID id;
  private final LifecycleEventRecorder events;
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<byte[]> sentPayloads = new CopyOnWriteArrayList<>();
  private final AtomicReference<ConnectorState> state = new AtomicReference<>(ConnectorState.NEW);
  private final AtomicReference<Exception> sendFailure = new AtomicReference<>();
  private final AtomicReference<RuntimeException> stopFailure = new AtomicReference<>();
  private final AtomicReference<SendBlock> sendBlock = new AtomicReference<>();
  private int sendAttempts;

  RecordingRawSocketConnector(UUID id) {
    this(id, null);
  }

  RecordingRawSocketConnector(UUID id, LifecycleEventRecorder events) {
    this.id = id;
    this.events = events;
  }

  @Override
  public UUID id() {
    return id;
  }

  @Override
  public ConnectorState state() {
    return state.get();
  }

  @Override
  public void start() {
    emitState(ConnectorState.CONNECTED);
    record("raw-continuation");
  }

  @Override
  public boolean stop() {
    emitState(ConnectorState.STOPPED);
    var failure = stopFailure.get();
    if (failure != null) {
      throw failure;
    }
    return true;
  }

  @Override
  public void send(byte[] data) throws Exception {
    synchronized (sentPayloads) {
      sendAttempts++;
      sentPayloads.notifyAll();
    }
    awaitSendRelease();
    var failure = sendFailure.get();
    if (failure != null) {
      throw failure;
    }
    sentPayloads.add(data.clone());
    record("raw-send");
    synchronized (sentPayloads) {
      sentPayloads.notifyAll();
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

  int listenerCount() {
    return listeners.size();
  }

  List<byte[]> sentPayloads() {
    return sentPayloads.stream().map(byte[]::clone).toList();
  }

  boolean awaitSentCount(int expected, java.time.Duration timeout) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    synchronized (sentPayloads) {
      while (sentPayloads.size() < expected) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(sentPayloads, remaining);
      }
      return true;
    }
  }

  void failSendsWith(Exception failure) {
    sendFailure.set(failure);
  }

  void failStopsWith(RuntimeException failure) {
    stopFailure.set(failure);
  }

  void blockSends(CountDownLatch entered, CountDownLatch interrupted, CountDownLatch release) {
    sendBlock.set(new SendBlock(entered, interrupted, release));
  }

  boolean awaitSendAttemptCount(int expected, java.time.Duration timeout)
      throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    synchronized (sentPayloads) {
      while (sendAttempts < expected) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(sentPayloads, remaining);
      }
      return true;
    }
  }

  void emitState(ConnectorState next) {
    state.set(next);
    listeners.forEach(listener -> listener.onStateChange(id, next));
  }

  void emitMessage(byte[] data) {
    listeners.forEach(listener -> listener.onMessage(id, data));
  }

  void emitError(Throwable error) {
    listeners.forEach(listener -> listener.onError(id, error));
  }

  void emitRestartRequested(Throwable reason) {
    listeners.forEach(listener -> listener.onRestartRequested(id, reason));
  }

  private void record(String event) {
    if (events != null) {
      events.record(event);
    }
  }

  private void awaitSendRelease() throws IOException {
    var block = sendBlock.get();
    if (block == null) {
      return;
    }
    block.entered().countDown();
    boolean interrupted = false;
    while (true) {
      try {
        block.release().await();
        break;
      } catch (InterruptedException _) {
        interrupted = true;
        block.interrupted().countDown();
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    if (state.get() != ConnectorState.CONNECTED) {
      throw new IOException("Raw connector closed during blocked send");
    }
  }

  private record SendBlock(
      CountDownLatch entered, CountDownLatch interrupted, CountDownLatch release) {}
}
