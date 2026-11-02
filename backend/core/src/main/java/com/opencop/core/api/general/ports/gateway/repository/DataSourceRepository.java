// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.repository;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.connection.setup.StoredConnectionSetup;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for data source metadata in the database.
 *
 * <p>Data sources are configuration/metadata only. Runtime connection state is read from Postgres
 * runtime observations written by Connect and Processor.
 *
 * <p>Core owns all CRUD operations on data sources.
 */
public interface DataSourceRepository {

  Optional<DataSource> findById(UUID id);

  Optional<DataSource> findOne(DataSourceReadFilter filter);

  List<DataSource> findAll(DataSourceReadFilter filter);

  /**
   * Returns a data source by id, including archived rows, while preserving the Kafka
   * broker-provider boundary used by connection reads.
   */
  default Optional<DataSource> findByIdForProvider(UUID id, String currentBrokerProvider) {
    return findOne(
        new DataSourceReadFilter(
            id, null, RecordVisibility.ALL, currentBrokerProvider, null, null));
  }

  /**
   * Returns all data sources, filtering Kafka sources to only those matching the current broker
   * provider. Socket sources (TCP/UDP) are always returned. Kafka sources with null brokerProvider
   * are excluded from all providers.
   */
  default List<DataSource> findAll(String currentBrokerProvider) {
    return findAll(
        new DataSourceReadFilter(
            null, Set.of(), RecordVisibility.ACTIVE, currentBrokerProvider, null, null));
  }

  default List<DataSource> findAll(
      String currentBrokerProvider, RecordVisibility recordVisibility) {
    return findAll(
        new DataSourceReadFilter(
            null, Set.of(), recordVisibility, currentBrokerProvider, null, null));
  }

  /** Count Kafka sources excluded by the broker provider filter (for startup warnings). */
  int countExcludedKafkaSources(String currentBrokerProvider);

  /**
   * Find the active data source already holding this listen port for the given transport, ignoring
   * host. Archived rows have released their port and are excluded.
   */
  Optional<UUID> findActiveByListenPort(TransportKind provider, int listenPort);

  /**
   * Persist a data source (upsert). Handles both SocketDataSource and KafkaDataSource.
   *
   * <p>A rejected write always surfaces as one of the port's own exceptions, never as a framework
   * one, so a caller that provisioned external resources first can release them without reaching
   * past this interface.
   *
   * @throws DataSourceNameConflictException another active data source holds the name
   * @throws DataSourceListenPortConflictException another active data source of the same transport
   *     holds the listen port
   * @throws DataSourceConstraintViolationException the database rejected the write for a reason
   *     this port does not model
   */
  Instant persist(DataSource dataSource);

  /**
   * Update the enabled flag (user intent for auto-start).
   *
   * @param actorId the user whose action drove the change, or null when system-initiated
   */
  Instant setEnabled(UUID sourceId, boolean enabled, UUID actorId);

  /** Archive a data source and clear runtime rows. */
  void archive(UUID sourceId, UUID archivedBy);

  /**
   * Set the assigned instance for a data source (Connect or Processor).
   *
   * <p>Assignment moves {@code updated_at}, so it must state the actor too — otherwise a
   * reconciliation or failover pairs a fresh timestamp with whichever human last edited the source.
   *
   * @param actorId the user whose action drove the change, or null when system-initiated
   */
  Instant setAssignedInstance(UUID sourceId, String instanceId, UUID actorId);

  /**
   * Clear the instance assignment (e.g., when connection is stopped).
   *
   * @param actorId the user whose action drove the change, or null when system-initiated
   */
  void clearAssignedInstance(UUID sourceId, UUID actorId);

  /**
   * Stores the non-secret part of the setup a data source was last submitted with. Joins the
   * caller's transaction.
   */
  void setSetupPrefill(UUID sourceId, SetupPrefill prefill);

  /** Returns the setup prefill a data source stores, empty when its setup was never captured. */
  Optional<SetupPrefill> findSetupPrefill(UUID sourceId);

  /** Returns the setup prefill of every listed data source that stores one. */
  Map<UUID, SetupPrefill> findSetupPrefills(Collection<UUID> sourceIds);

  /**
   * Replaces a connection's setup: its spec, integration, access mode and mappers, and setup
   * prefill. Its name, visibility, ownership and assignment are left as they are. Joins the
   * caller's transaction. Integration settings stored for another integration or schema version
   * must be deleted first: they reference the connection under the one they were validated for.
   *
   * @throws DataSourceListenPortConflictException when the new setup listens on a port another
   *     active source reserves
   */
  void updateSetup(UUID sourceId, StoredConnectionSetup setup, UUID updatedBy);
}
