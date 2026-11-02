// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.core.sdk.business.policy.LegacySocketAccessModePolicy;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DB-validated local lifecycle operations for Connect.
 *
 * <p>Called by scheduled reconciliation sweeps and by local connector listeners for in-process
 * restart requests. Desired ownership comes from Postgres in every path.
 */
public class ConnectionLifecycleService {

  private static final Logger log = LoggerFactory.getLogger(ConnectionLifecycleService.class);

  private final ConnectionManager connectionManager;
  private final DesiredSocketSourceRepository desiredRepository;
  private final ConnectorListener defaultListener;
  private final String instanceId;
  private final ConnectorListener restartRequestListener = new RestartRequestListener();
  private final ConcurrentMap<UUID, Long> lastAppliedRestartEpoch = new ConcurrentHashMap<>();
  private final Object restartLock = new Object();

  public ConnectionLifecycleService(
      ConnectionManager connectionManager,
      DesiredSocketSourceRepository desiredRepository,
      ConnectorListener defaultListener,
      String instanceId) {
    this.connectionManager = connectionManager;
    this.desiredRepository = desiredRepository;
    this.defaultListener = defaultListener;
    this.instanceId = instanceId;
  }

  public ConnectionManager.StartOutcome ensureStarted(DesiredSocketSource desired) {
    var source = desired.source();
    if (LegacySocketAccessModePolicy.isIncompatible(source)) {
      connectionManager.ensureIncompatible(source.id(), source.provider(), source.access());
      return ConnectionManager.StartOutcome.INCOMPATIBLE;
    }
    return connectionManager.ensureStarted(
        creationContext(desired), source.provider(), source.access(), connectorListeners());
  }

  /**
   * Applies the source's desired integration settings to its running connector; the socket stays
   * open.
   *
   * @return whether the behavior plan was swapped
   */
  public boolean applyDesiredSettings(DesiredSocketSource desired) {
    return connectionManager.applyDesiredSettings(creationContext(desired));
  }

  private static ConnectorCreationContext creationContext(DesiredSocketSource desired) {
    var source = desired.source();
    return new ConnectorCreationContext(
        source.id(), source.spec(), source.integration(), desired.overrides());
  }

  public boolean ensureStopped(UUID sourceId) {
    return connectionManager.ensureStopped(sourceId);
  }

  public boolean ensureIncompatible(SocketDataSource source) {
    return connectionManager.ensureIncompatible(source.id(), source.provider(), source.access());
  }

  public boolean applyDesiredAccess(SocketDataSource source) {
    return connectionManager.applyDesiredAccess(source.id(), source.access());
  }

  public Optional<ConnectionManager.StartOutcome> restartIfDesired(UUID sourceId) {
    synchronized (restartLock) {
      Optional<DesiredSocketSource> row;
      try {
        row = desiredRepository.findSocketSourceById(sourceId);
      } catch (Exception e) {
        log.warn(
            "Skipping local restart request for source {} because desired ownership could not be read: {}",
            sourceId,
            e.getMessage());
        return Optional.empty();
      }

      return applyLocalRestart(row.orElse(null), sourceId);
    }
  }

  public Optional<ConnectionManager.StartOutcome> restartIfDesired(
      DesiredSocketSource desired, long epoch) {
    synchronized (restartLock) {
      if (restartEpochApplied(desired.id(), epoch)) {
        return Optional.empty();
      }

      return applyRestartEpoch(desired, desired.id(), epoch);
    }
  }

  public Optional<ConnectionManager.StartOutcome> restartFromDesired(DesiredSocketSource desired) {
    synchronized (restartLock) {
      if (desired == null) {
        return Optional.empty();
      }

      return applyNonEpochRestart(desired, desired.id(), "desired-state restart");
    }
  }

  public boolean hasUnappliedRestartEpoch(UUID sourceId, long epoch) {
    return epoch > 0 && lastAppliedRestartEpoch.getOrDefault(sourceId, 0L) < epoch;
  }

  public void markRestartEpochApplied(UUID sourceId, long epoch) {
    if (epoch <= 0) {
      return;
    }
    lastAppliedRestartEpoch.merge(sourceId, epoch, Math::max);
  }

  private Optional<ConnectionManager.StartOutcome> applyRestartEpoch(
      DesiredSocketSource desired, UUID sourceId, long epoch) {
    if (desired == null || !isDesiredHere(desired.source())) {
      var stopped = connectionManager.ensureStopped(sourceId);
      markRestartEpochApplied(sourceId, epoch);
      log.info(
          "Applied restart epoch {} for source {} as stop-only because DB no longer desires it here; stopped={}",
          epoch,
          sourceId,
          stopped);
      return Optional.empty();
    }

    var source = desired.source();
    if (LegacySocketAccessModePolicy.isIncompatible(source)) {
      connectionManager.ensureIncompatible(source.id(), source.provider(), source.access());
      markRestartEpochApplied(sourceId, epoch);
      return Optional.of(ConnectionManager.StartOutcome.INCOMPATIBLE);
    }

    connectionManager.ensureStopped(sourceId);
    var outcome = ensureStarted(desired);
    markRestartEpochApplied(sourceId, epoch);
    log.info("Applied restart epoch {} for source {} with outcome {}", epoch, sourceId, outcome);
    return Optional.of(outcome);
  }

  private Optional<ConnectionManager.StartOutcome> applyLocalRestart(
      DesiredSocketSource desired, UUID sourceId) {
    return applyNonEpochRestart(desired, sourceId, "local restart request");
  }

  private Optional<ConnectionManager.StartOutcome> applyNonEpochRestart(
      DesiredSocketSource desired, UUID sourceId, String reason) {
    if (desired == null || !isDesiredHere(desired.source())) {
      var stopped = connectionManager.ensureStopped(sourceId);
      log.info(
          "Applied {} for source {} as stop-only because DB no longer desires it here; stopped={}",
          reason,
          sourceId,
          stopped);
      return Optional.empty();
    }

    var source = desired.source();
    if (LegacySocketAccessModePolicy.isIncompatible(source)) {
      connectionManager.ensureIncompatible(source.id(), source.provider(), source.access());
      return Optional.of(ConnectionManager.StartOutcome.INCOMPATIBLE);
    }

    connectionManager.ensureStopped(sourceId);
    var outcome = ensureStarted(desired);
    log.info("Applied {} for source {} with outcome {}", reason, sourceId, outcome);
    return Optional.of(outcome);
  }

  private boolean restartEpochApplied(UUID sourceId, long epoch) {
    return epoch <= 0 || lastAppliedRestartEpoch.getOrDefault(sourceId, 0L) >= epoch;
  }

  private boolean isDesiredHere(SocketDataSource source) {
    return source.enabled() && instanceId.equals(source.assignedInstance());
  }

  private ConnectorListener[] connectorListeners() {
    return new ConnectorListener[] {defaultListener, restartRequestListener};
  }

  private final class RestartRequestListener implements ConnectorListener {
    /**
     * Handles connector-requested receive-idle recovery in-process.
     *
     * <p>Each request reloads the persisted desired state and restarts the source only when it is
     * enabled and assigned to this instance. A source that is no longer desired here is stopped.
     */
    @Override
    public void onRestartRequested(UUID sourceId, Throwable reason) {
      var message = reason == null ? "unspecified" : reason.getMessage();
      ConnectionLifecycleService.log.warn(
          "Applying direct local restart request for source {}: {}", sourceId, message);
      restartIfDesired(sourceId);
    }
  }
}
