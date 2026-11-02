// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.connection.setup.StoredConnectionSetup;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Test repository decorator that exposes aggregate versus per-source metadata reads. */
public final class TrackingDataSourceRepository implements DataSourceRepository {

  private final DataSourceRepository delegate;
  private final AtomicInteger aggregateReadCount = new AtomicInteger();
  private final AtomicInteger singleReadCount = new AtomicInteger();

  public TrackingDataSourceRepository(DataSourceRepository delegate) {
    this.delegate = delegate;
  }

  @Override
  public Optional<DataSource> findById(UUID id) {
    singleReadCount.incrementAndGet();
    return delegate.findById(id);
  }

  @Override
  public Optional<DataSource> findOne(DataSourceReadFilter filter) {
    singleReadCount.incrementAndGet();
    return delegate.findOne(filter);
  }

  @Override
  public List<DataSource> findAll(DataSourceReadFilter filter) {
    aggregateReadCount.incrementAndGet();
    return delegate.findAll(filter);
  }

  @Override
  public int countExcludedKafkaSources(String currentBrokerProvider) {
    return delegate.countExcludedKafkaSources(currentBrokerProvider);
  }

  @Override
  public Optional<UUID> findActiveByListenPort(TransportKind provider, int listenPort) {
    return delegate.findActiveByListenPort(provider, listenPort);
  }

  @Override
  public Instant persist(DataSource dataSource) {
    return delegate.persist(dataSource);
  }

  @Override
  public Instant setEnabled(UUID sourceId, boolean enabled, UUID actorId) {
    return delegate.setEnabled(sourceId, enabled, actorId);
  }

  @Override
  public void archive(UUID sourceId, UUID archivedBy) {
    delegate.archive(sourceId, archivedBy);
  }

  @Override
  public Instant setAssignedInstance(UUID sourceId, String instanceId, UUID actorId) {
    return delegate.setAssignedInstance(sourceId, instanceId, actorId);
  }

  @Override
  public void clearAssignedInstance(UUID sourceId, UUID actorId) {
    delegate.clearAssignedInstance(sourceId, actorId);
  }

  @Override
  public void updateSetup(UUID sourceId, StoredConnectionSetup setup, UUID updatedBy) {
    delegate.updateSetup(sourceId, setup, updatedBy);
  }

  @Override
  public void setSetupPrefill(UUID sourceId, SetupPrefill prefill) {
    delegate.setSetupPrefill(sourceId, prefill);
  }

  @Override
  public Optional<SetupPrefill> findSetupPrefill(UUID sourceId) {
    return delegate.findSetupPrefill(sourceId);
  }

  @Override
  public Map<UUID, SetupPrefill> findSetupPrefills(Collection<UUID> sourceIds) {
    return delegate.findSetupPrefills(sourceIds);
  }

  public void resetReadCounts() {
    aggregateReadCount.set(0);
    singleReadCount.set(0);
  }

  public int aggregateReadCount() {
    return aggregateReadCount.get();
  }

  public int singleReadCount() {
    return singleReadCount.get();
  }
}
