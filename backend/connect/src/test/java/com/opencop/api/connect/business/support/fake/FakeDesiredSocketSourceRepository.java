// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support.fake;

import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FakeDesiredSocketSourceRepository implements DesiredSocketSourceRepository {

  private final ConcurrentMap<UUID, DesiredSocketSource> rows = new ConcurrentHashMap<>();
  private final AtomicBoolean failReads = new AtomicBoolean(false);
  private volatile Runnable findAssignedHook;
  private volatile Runnable findByIdsHook;

  public void put(SocketDataSource source) {
    put(new DesiredSocketSource(source, Optional.empty()));
  }

  public void put(DesiredSocketSource desired) {
    rows.put(desired.id(), desired);
  }

  public void remove(UUID sourceId) {
    rows.remove(sourceId);
  }

  public void setFailReads(boolean failReads) {
    this.failReads.set(failReads);
  }

  public void setFindAssignedHook(Runnable findAssignedHook) {
    this.findAssignedHook = findAssignedHook;
  }

  public void setFindByIdsHook(Runnable findByIdsHook) {
    this.findByIdsHook = findByIdsHook;
  }

  @Override
  public String diagnosticLabel() {
    return "fake desired-state repository";
  }

  @Override
  public Optional<DesiredSocketSource> findSocketSourceById(UUID sourceId) {
    failIfRequested();
    return Optional.ofNullable(rows.get(sourceId));
  }

  @Override
  public List<DesiredSocketSource> findEnabledSocketSourcesAssignedTo(String instanceId) {
    failIfRequested();
    var hook = findAssignedHook;
    if (hook != null) {
      hook.run();
    }
    return rows.values().stream()
        .filter(
            desired ->
                desired.source().enabled()
                    && instanceId.equals(desired.source().assignedInstance()))
        .toList();
  }

  @Override
  public Map<UUID, SocketDataSource> findSocketSourcesByIds(Set<UUID> sourceIds) {
    failIfRequested();
    var hook = findByIdsHook;
    if (hook != null) {
      hook.run();
    }
    var found = new HashMap<UUID, SocketDataSource>();
    for (var sourceId : sourceIds) {
      var row = rows.get(sourceId);
      if (row != null) {
        found.put(sourceId, row.source());
      }
    }
    return found;
  }

  private void failIfRequested() {
    if (failReads.get()) {
      throw new IllegalStateException("desired state unavailable");
    }
  }
}
