// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for fetching data sources with their runtime status.
 *
 * <p>Combines metadata from Core DB with runtime status from Postgres observations and Kafka broker
 * checks.
 *
 * <ul>
 *   <li>Batch DB lookups (single query for multiple IDs)
 *   <li>Socket runtime from Postgres observations (written by Connect)
 *   <li>KAFKA state from broker checks (topic + ACL), lastMessage from Postgres observations
 *   <li>Discrepancy detection between DB enabled flag and runtime state
 * </ul>
 */
public class DataSourceRuntimeService {

  private static final Logger log = LoggerFactory.getLogger(DataSourceRuntimeService.class);

  private final DataSourceRepository dataSourceRepository;
  private final String currentBrokerProvider;
  private final KafkaProvisioningService kafkaProvisioner;
  @Nullable private final SourceRuntimeObservationReader sourceRuntimeObservationReader;
  @Nullable private final ConnectInstanceResolver connectInstanceResolver;

  /** Cache for topic existence. Short TTL for near-real-time parity with TCP runtime checks. */
  private final Cache<String, Boolean> topicExistsCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5)).maximumSize(500).build();

  /** Cache for WRITE ACL existence. Same TTL — ACLs change on stop/start only. */
  private final Cache<String, Boolean> aclExistsCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5)).maximumSize(500).build();

  public DataSourceRuntimeService(
      DataSourceRepository dataSourceRepository,
      CurrentBrokerProvider currentBrokerProvider,
      KafkaProvisioningService kafkaProvisioner,
      @Nullable SourceRuntimeObservationReader sourceRuntimeObservationReader,
      @Nullable ConnectInstanceResolver connectInstanceResolver) {
    this.dataSourceRepository = dataSourceRepository;
    this.currentBrokerProvider = currentBrokerProvider.name();
    this.kafkaProvisioner = kafkaProvisioner;
    this.sourceRuntimeObservationReader = sourceRuntimeObservationReader;
    this.connectInstanceResolver = connectInstanceResolver;
  }

  /**
   * Fetches data sources with their runtime status in bulk.
   *
   * @param ids the data source IDs to fetch
   * @return map of ID to DataSourceRuntime (only includes IDs that exist in DB)
   */
  public Map<UUID, DataSourceRuntime> getWithRuntime(Set<UUID> ids) {
    return getWithRuntime(ids, RecordVisibility.ACTIVE);
  }

  public Map<UUID, DataSourceRuntime> getWithRuntime(
      Set<UUID> ids, RecordVisibility recordVisibility) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }

    var filter =
        new DataSourceReadFilter(null, ids, recordVisibility, currentBrokerProvider, null, null);
    List<DataSource> dataSources = dataSourceRepository.findAll(filter);
    var freshConnectInstanceIds = freshConnectInstanceIds(dataSources);

    Map<UUID, DataSourceRuntime> result = new HashMap<>();
    for (DataSource ds : dataSources) {
      switch (ds) {
        case KafkaDataSource kafka -> {
          var state = resolveKafkaState(kafka);
          var lastMessageAt = readLastMessageFromObservation(ds.id());
          result.put(
              ds.id(),
              new DataSourceRuntime(ds, new RuntimeStatus(ds.id(), state, lastMessageAt, null)));
        }
        case SocketDataSource socket -> {
          var runtime = readEffectiveSocketRuntime(socket, freshConnectInstanceIds);
          logIfDiscrepancy(ds.id(), ds.name(), ds.enabled(), runtime.state());
          result.put(ds.id(), new DataSourceRuntime(ds, runtime));
        }
      }
    }

    return result;
  }

  /**
   * Fetches a single data source with its runtime status.
   *
   * @param id the data source ID
   * @return the DataSourceRuntime, or null if not found in DB
   */
  public DataSourceRuntime getWithRuntime(UUID id) {
    return getWithRuntime(id, RecordVisibility.ACTIVE);
  }

  public DataSourceRuntime getWithRuntime(UUID id, RecordVisibility recordVisibility) {
    var result = getWithRuntime(Set.of(id), recordVisibility);
    return result.get(id);
  }

  /**
   * Determines KAFKA source state by checking live broker state (topic existence + WRITE ACL).
   * Analogous to Connect's runtime check for TCP/UDP. Returns UNKNOWN if broker is unreachable
   * (same as TCP returns UNKNOWN when Connect is unreachable).
   */
  private ConnectorState resolveKafkaState(KafkaDataSource kafka) {
    if (kafka.topicName() == null || kafka.username() == null) return ConnectorState.UNKNOWN;

    // Check topic existence (cached — only calls broker on cache miss, null = not cached)
    var topicExists =
        topicExistsCache.get(
            kafka.topicName(), k -> kafkaProvisioner.checkTopicExists(k).orElse(null));
    if (topicExists == null) return ConnectorState.UNKNOWN;
    if (!topicExists) return ConnectorState.DISCONNECTED;

    // Check WRITE ACL (cached — same pattern)
    var aclKey = kafka.username() + ":" + kafka.topicName();
    var hasAcl =
        aclExistsCache.get(
            aclKey,
            k -> kafkaProvisioner.hasWriteAcl(kafka.username(), kafka.topicName()).orElse(null));
    return hasAcl ? ConnectorState.CONNECTED : ConnectorState.STOPPED;
  }

  /**
   * Reads the latest fresh observed lastMessage timestamp. Socket runtime is a special case because
   * it must be scoped to the assigned fresh Connect instance to avoid stale or wrong-owner
   * observations; see {@link #readEffectiveSocketRuntime(SocketDataSource, Set)}.
   *
   * <p>Returns null on miss/failure.
   */
  private Instant readLastMessageFromObservation(UUID sourceId) {
    if (sourceRuntimeObservationReader == null) return null;
    try {
      return sourceRuntimeObservationReader
          .findFresh(sourceId)
          .map(RuntimeStatus::lastMessageAt)
          .orElse(null);
    } catch (Exception e) {
      log.debug(
          "Postgres read failed for source runtime observation {}: {}", sourceId, e.getMessage());
      return null;
    }
  }

  private RuntimeStatus readEffectiveSocketRuntime(
      SocketDataSource source, Set<String> freshConnectInstanceIds) {
    if (!source.enabled()) return RuntimeStatus.stopped(source.id());

    var assignedInstance = source.assignedInstance();
    if (assignedInstance == null || !freshConnectInstanceIds.contains(assignedInstance)) {
      return RuntimeStatus.unknown(source.id());
    }

    if (sourceRuntimeObservationReader == null) return RuntimeStatus.unknown(source.id());

    try {
      return sourceRuntimeObservationReader
          .findFreshForInstance(source.id(), assignedInstance)
          .orElseGet(() -> RuntimeStatus.unknown(source.id()));
    } catch (Exception e) {
      log.debug("Postgres read failed for socket runtime {}: {}", source.id(), e.getMessage());
      return RuntimeStatus.unknown(source.id());
    }
  }

  private Set<String> freshConnectInstanceIds(List<DataSource> dataSources) {
    if (connectInstanceResolver == null
        || dataSources.stream().noneMatch(SocketDataSource.class::isInstance)) {
      return Set.of();
    }

    try {
      var instanceIds = connectInstanceResolver.getAllInstanceIds();
      return instanceIds == null ? Set.of() : Set.copyOf(instanceIds);
    } catch (Exception e) {
      log.debug("Postgres read failed for Connect worker health: {}", e.getMessage());
      return Set.of();
    }
  }

  /** Logs a warning if the DB enabled flag doesn't match the runtime state. */
  private void logIfDiscrepancy(
      UUID id, String name, boolean enabled, ConnectorState runtimeState) {
    if (enabled && runtimeState == ConnectorState.STOPPED) {
      log.warn(
          "State discrepancy: source '{}' ({}) is enabled but runtime state is STOPPED"
              + " - auto-start should start it on next Core startup",
          name,
          id);
    } else if (!enabled && runtimeState.isActive()) {
      log.warn(
          "State discrepancy: source '{}' ({}) is disabled but runtime state is {}"
              + " - connection should have been stopped",
          name,
          id,
          runtimeState);
    }
  }
}
