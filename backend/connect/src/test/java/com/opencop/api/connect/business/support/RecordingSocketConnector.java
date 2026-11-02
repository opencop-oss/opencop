// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RecordingSocketConnector implements SocketConnector {

  private final UUID id;
  private final ConnectionSpec spec;
  private final CopyOnWriteArrayList<ConnectorListener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicReference<ConnectorState> state = new AtomicReference<>(ConnectorState.NEW);
  private final AtomicInteger stopCount = new AtomicInteger();

  RecordingSocketConnector(UUID id, ConnectionSpec spec) {
    this.id = id;
    this.spec = spec;
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
  }

  @Override
  public boolean stop() {
    stopCount.incrementAndGet();
    state.set(ConnectorState.STOPPED);
    return true;
  }

  @Override
  public void addListener(ConnectorListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(ConnectorListener listener) {
    listeners.remove(listener);
  }

  public int stopCount() {
    return stopCount.get();
  }

  public int listenerCount() {
    return listeners.size();
  }

  public ConnectionSpec spec() {
    return spec;
  }

  public void setState(ConnectorState state) {
    this.state.set(state);
  }

  public void emitMessage(byte[] payload) {
    for (var listener : listeners) {
      listener.onMessage(id, payload);
    }
  }

  public void emitState(ConnectorState state) {
    this.state.set(state);
    for (var listener : listeners) {
      listener.onStateChange(id, state);
    }
  }

  public void emitRestartRequested(Throwable reason) {
    for (var listener : listeners) {
      listener.onRestartRequested(id, reason);
    }
  }
}
