// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class RecordingSocketConnectorFactory implements ConnectorFactory {

  private final ConcurrentMap<UUID, CopyOnWriteArrayList<RecordingSocketConnector>> connectors =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CopyOnWriteArrayList<RawCreation>> creations =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AtomicInteger> createAttempts = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AtomicInteger> createFailuresRemaining =
      new ConcurrentHashMap<>();
  private final Set<UUID> createFailures = ConcurrentHashMap.newKeySet();

  @Override
  public SocketConnector create(UUID sourceId, ConnectionSpec specification) {
    createAttempts.computeIfAbsent(sourceId, _ -> new AtomicInteger()).incrementAndGet();
    var failuresRemaining = createFailuresRemaining.get(sourceId);
    if (failuresRemaining != null
        && failuresRemaining.getAndUpdate(count -> Math.max(0, count - 1)) > 0) {
      throw new IllegalStateException("create failed for " + sourceId);
    }
    if (createFailures.contains(sourceId)) {
      throw new IllegalStateException("create failed for " + sourceId);
    }
    var connector = new RecordingSocketConnector(sourceId, specification);
    connectors.computeIfAbsent(sourceId, _ -> new CopyOnWriteArrayList<>()).add(connector);
    creations
        .computeIfAbsent(sourceId, _ -> new CopyOnWriteArrayList<>())
        .add(new RawCreation(specification));
    return connector;
  }

  public void failCreateFor(UUID sourceId) {
    createFailures.add(sourceId);
  }

  public void failCreateOnceFor(UUID sourceId) {
    failNextCreatesFor(sourceId, 1);
  }

  public void failNextCreatesFor(UUID sourceId, int count) {
    if (count <= 0) {
      return;
    }
    createFailuresRemaining.computeIfAbsent(sourceId, _ -> new AtomicInteger()).addAndGet(count);
  }

  public RecordingSocketConnector latest(UUID sourceId) {
    return connectors.get(sourceId).getLast();
  }

  public int createdCount(UUID sourceId) {
    var sourceConnectors = connectors.get(sourceId);
    return sourceConnectors == null ? 0 : sourceConnectors.size();
  }

  public RawCreation latestCreation(UUID sourceId) {
    return creations.get(sourceId).getLast();
  }

  public int createAttemptCount(UUID sourceId) {
    var attempts = createAttempts.get(sourceId);
    return attempts == null ? 0 : attempts.get();
  }

  public record RawCreation(ConnectionSpec specification) {}
}
