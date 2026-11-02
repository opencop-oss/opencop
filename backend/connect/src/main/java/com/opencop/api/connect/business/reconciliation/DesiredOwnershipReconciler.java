// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.reconciliation;

import com.opencop.api.connect.business.connector.ConnectionLifecycleService;
import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.core.sdk.business.policy.LegacySocketAccessModePolicy;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reconciles local Connect runtime toward Core DB desired socket ownership. */
public class DesiredOwnershipReconciler {

  private static final Logger log = LoggerFactory.getLogger(DesiredOwnershipReconciler.class);

  private final ConnectionManager connectionManager;
  private final DesiredSocketSourceRepository desiredRepository;
  private final ConnectionLifecycleService lifecycleService;
  private final String instanceId;
  private final Duration recoveryMinInterval;
  private final Clock clock;
  private final SourceRestartEpochReader restartEpochReader;
  private final DesiredStateOutagePolicy outagePolicy;
  private final AtomicBoolean running = new AtomicBoolean(false);
  // Mutable reconciliation state below is serialized by reconcileOnce() through the running guard.
  // Per-source terminal recovery backoff. recoveryMinInterval is intentionally distinct from the
  // scheduler tick interval, and is usually longer, so one flapping source is not restarted every
  // reconciliation tick.
  private final Map<UUID, RecoveryBackoff> nextRecoveryAttemptAt = new HashMap<>();
  private Instant desiredStateOutageStartedAt;

  public DesiredOwnershipReconciler(
      ConnectionManager connectionManager,
      DesiredSocketSourceRepository desiredRepository,
      ConnectionLifecycleService lifecycleService,
      String instanceId,
      Duration recoveryMinInterval,
      SourceRestartEpochReader restartEpochReader,
      DesiredStateOutagePolicy outagePolicy) {
    this(
        connectionManager,
        desiredRepository,
        lifecycleService,
        instanceId,
        recoveryMinInterval,
        restartEpochReader,
        outagePolicy,
        Clock.systemUTC());
  }

  DesiredOwnershipReconciler(
      ConnectionManager connectionManager,
      DesiredSocketSourceRepository desiredRepository,
      ConnectionLifecycleService lifecycleService,
      String instanceId,
      Duration recoveryMinInterval,
      SourceRestartEpochReader restartEpochReader,
      DesiredStateOutagePolicy outagePolicy,
      Clock clock) {
    this.connectionManager = connectionManager;
    this.desiredRepository = desiredRepository;
    this.lifecycleService = lifecycleService;
    this.instanceId = instanceId;
    this.recoveryMinInterval =
        recoveryMinInterval == null || recoveryMinInterval.isNegative()
            ? Duration.ZERO
            : recoveryMinInterval;
    this.restartEpochReader = restartEpochReader;
    this.outagePolicy =
        outagePolicy == null ? DesiredStateOutagePolicy.continueExisting() : outagePolicy;
    this.clock = clock;
  }

  public record ReconciliationResult(
      int desiredCount,
      int localCount,
      int startedCount,
      int stoppedCount,
      int connectedRestartedCount,
      int lifecycleFailureCount,
      boolean skipped,
      boolean failed) {
    public ReconciliationResult(
        int desiredCount,
        int localCount,
        int startedCount,
        int stoppedCount,
        boolean skipped,
        boolean failed) {
      this(desiredCount, localCount, startedCount, stoppedCount, 0, 0, skipped, failed);
    }

    static ReconciliationResult skippedResult() {
      return new ReconciliationResult(0, 0, 0, 0, true, false);
    }

    static ReconciliationResult failedResult(int localCount) {
      return new ReconciliationResult(0, localCount, 0, 0, false, true);
    }

    static ReconciliationResult failedResult(int localCount, int stoppedCount) {
      return new ReconciliationResult(0, localCount, 0, stoppedCount, false, true);
    }
  }

  public enum ReconciliationMode {
    NORMAL,
    FULL
  }

  // DB restart epoch observed when this recovery backoff was scheduled or pre-claimed.
  private record RecoveryBackoff(Instant nextAttemptAt, long restartEpochAtBackoff) {}

  private record StaleLocalStopResult(int stoppedCount, Set<UUID> staleSourceIds) {}

  private record SourceReconciliationResult(
      int startedCount, int stoppedCount, int connectedRestartedCount, int lifecycleFailureCount) {
    static SourceReconciliationResult none() {
      return new SourceReconciliationResult(0, 0, 0, 0);
    }

    static SourceReconciliationResult started() {
      return new SourceReconciliationResult(1, 0, 0, 0);
    }

    static SourceReconciliationResult lifecycleFailure() {
      return new SourceReconciliationResult(0, 0, 0, 1);
    }
  }

  public ReconciliationResult reconcileOnce() {
    return reconcileOnce(ReconciliationMode.NORMAL);
  }

  public ReconciliationResult reconcileOnce(ReconciliationMode mode) {
    if (!running.compareAndSet(false, true)) {
      log.debug("Desired ownership reconciliation already running for {}", instanceId);
      return ReconciliationResult.skippedResult();
    }

    try {
      return doReconcileOnce(mode == null ? ReconciliationMode.NORMAL : mode);
    } finally {
      running.set(false);
    }
  }

  private ReconciliationResult doReconcileOnce(ReconciliationMode mode) {
    var localRuntime = connectionManager.getAllRuntime();
    List<DesiredSocketSource> desiredSources;
    Map<UUID, SocketDataSource> desiredRowsByLocalRuntimeId;
    Map<UUID, Long> restartEpochs;
    try {
      desiredSources = desiredRepository.findEnabledSocketSourcesAssignedTo(instanceId);
      desiredRowsByLocalRuntimeId =
          desiredRepository.findSocketSourcesByIds(sourceIds(localRuntime));
      restartEpochs = findRestartEpochs(desiredSources, localRuntime);
      if (hasUnappliedRestartEpoch(restartEpochs)) {
        // A source's row and its epoch commit together, so rows read after the epochs carry at
        // least the state each pending epoch was committed with.
        desiredSources = desiredRepository.findEnabledSocketSourcesAssignedTo(instanceId);
        desiredRowsByLocalRuntimeId =
            desiredRepository.findSocketSourcesByIds(sourceIds(localRuntime));
      }
      desiredStateOutageStartedAt = null;
    } catch (Exception e) {
      return handleDesiredStateReadFailure(localRuntime, e);
    }

    var staleLocalStopResult = stopUndesiredLocalSources(localRuntime, desiredRowsByLocalRuntimeId);

    var runtimeById = new HashMap<UUID, RuntimeStatus>();
    for (var runtime : localRuntime) {
      runtimeById.put(runtime.id(), runtime);
    }

    var started = 0;
    var stopped = staleLocalStopResult.stoppedCount();
    var connectedRestarted = 0;
    var lifecycleFailures = 0;
    for (var desired : desiredSources) {
      var sourceId = desired.id();
      var runtime = runtimeById.get(sourceId);
      var restartEpoch = restartEpochs.getOrDefault(sourceId, 0L);
      var result =
          reconcileDesiredSource(
              desired,
              runtime,
              restartEpoch,
              staleLocalStopResult.staleSourceIds().contains(sourceId),
              mode);
      started += result.startedCount();
      stopped += result.stoppedCount();
      connectedRestarted += result.connectedRestartedCount();
      lifecycleFailures += result.lifecycleFailureCount();
    }

    if (started > 0 || stopped > 0 || connectedRestarted > 0 || lifecycleFailures > 0) {
      log.info(
          "Desired ownership reconciliation for {} started/recovered {}, stopped {}, connected-restarted {}, and failed lifecycle actions for {} sources",
          instanceId,
          started,
          stopped,
          connectedRestarted,
          lifecycleFailures);
    }

    return new ReconciliationResult(
        desiredSources.size(),
        localRuntime.size(),
        started,
        stopped,
        connectedRestarted,
        lifecycleFailures,
        false,
        false);
  }

  private StaleLocalStopResult stopUndesiredLocalSources(
      List<RuntimeStatus> localRuntime, Map<UUID, SocketDataSource> desiredRowsByLocalRuntimeId) {
    var stopped = 0;
    // Prevent a FULL pass from restarting a source from the first desired-state snapshot after the
    // second desired-state read found that the local runtime is no longer desired here.
    var staleLocalSourceIds = new HashSet<UUID>();
    for (var runtime : localRuntime) {
      var row = desiredRowsByLocalRuntimeId.get(runtime.id());
      if (row != null && row.enabled() && instanceId.equals(row.assignedInstance())) {
        continue;
      }

      staleLocalSourceIds.add(runtime.id());
      nextRecoveryAttemptAt.remove(runtime.id());
      try {
        if (lifecycleService.ensureStopped(runtime.id())) {
          stopped++;
        }
      } catch (Exception e) {
        logLifecycleFailure(runtime.id(), "stop", e);
      }
    }
    return new StaleLocalStopResult(stopped, staleLocalSourceIds);
  }

  private SourceReconciliationResult reconcileDesiredSource(
      DesiredSocketSource desired,
      RuntimeStatus runtime,
      long restartEpoch,
      boolean staleLocalSource,
      ReconciliationMode mode) {
    var sourceId = desired.id();
    var hasUnappliedRestartEpoch =
        lifecycleService.hasUnappliedRestartEpoch(sourceId, restartEpoch);
    var actionToLog = "process";
    var recoveryAlreadyClaimed = false;

    try {
      if (runtime != null && staleLocalSource) {
        return SourceReconciliationResult.none();
      }

      if (LegacySocketAccessModePolicy.isIncompatible(desired.source())) {
        actionToLog = "mark incompatible";
        var stopped = lifecycleService.ensureIncompatible(desired.source());
        lifecycleService.markRestartEpochApplied(sourceId, restartEpoch);
        clearRecoveryAttempt(sourceId);
        return new SourceReconciliationResult(0, stopped ? 1 : 0, 0, 0);
      }

      if (runtime == null
          || (runtime.state() == ConnectorState.INCOMPATIBLE
              && !connectionManager.isLoaded(sourceId))) {
        actionToLog = "start";
        return startMissingRuntime(desired, restartEpoch, hasUnappliedRestartEpoch);
      }

      if (hasUnappliedRestartEpoch) {
        actionToLog = "restart";
        return restartForUnappliedEpoch(desired, restartEpoch, hasUnappliedRestartEpoch);
      }

      if (mode == ReconciliationMode.FULL && runtime.state() == ConnectorState.CONNECTED) {
        actionToLog = "restart connected";
        return restartConnectedFromDesiredState(desired);
      }

      if (!shouldRecover(runtime.state())) {
        lifecycleService.applyDesiredAccess(desired.source());
        applyDesiredSettings(desired);
        clearRecoveryAttempt(sourceId);
        return SourceReconciliationResult.none();
      }

      if (!claimRecoveryAttempt(sourceId, runtime.state(), restartEpoch)) {
        return SourceReconciliationResult.none();
      }
      recoveryAlreadyClaimed = true;

      actionToLog = "recover";
      return recoverTerminalRuntime(desired);
    } catch (Exception e) {
      if (!recoveryAlreadyClaimed) {
        scheduleNextRecoveryAttempt(sourceId, restartEpoch);
      }
      logLifecycleFailure(sourceId, actionToLog, e);
      return SourceReconciliationResult.lifecycleFailure();
    }
  }

  /**
   * Swaps a running connector's behavior plan when its effective settings changed. Settings never
   * restart the socket, so a failure here is logged and leaves the runtime, its restart epoch and
   * its recovery schedule untouched.
   */
  private void applyDesiredSettings(DesiredSocketSource desired) {
    try {
      if (lifecycleService.applyDesiredSettings(desired)) {
        log.info("Applied changed integration settings to connection {}", desired.id());
      }
    } catch (Exception e) {
      log.warn(
          "Could not apply integration settings to connection {}: {}",
          desired.id(),
          e.getMessage(),
          e);
    }
  }

  private SourceReconciliationResult startMissingRuntime(
      DesiredSocketSource desired, long restartEpoch, boolean hasUnappliedRestartEpoch) {
    var sourceId = desired.id();
    if (recoveryBackoffActive(sourceId, restartEpoch, hasUnappliedRestartEpoch)) {
      return SourceReconciliationResult.none();
    }

    var outcome = lifecycleService.ensureStarted(desired);
    lifecycleService.markRestartEpochApplied(sourceId, restartEpoch);
    settleRecoveryAttempt(sourceId, restartEpoch, outcome);
    if (outcome == ConnectionManager.StartOutcome.STARTED
        || outcome == ConnectionManager.StartOutcome.RESTARTED) {
      return SourceReconciliationResult.started();
    }
    return SourceReconciliationResult.none();
  }

  private SourceReconciliationResult restartForUnappliedEpoch(
      DesiredSocketSource desired, long restartEpoch, boolean hasUnappliedRestartEpoch) {
    var sourceId = desired.id();
    if (recoveryBackoffActive(sourceId, restartEpoch, hasUnappliedRestartEpoch)) {
      return SourceReconciliationResult.none();
    }

    var outcome = lifecycleService.restartIfDesired(desired, restartEpoch);
    settleRecoveryAttempt(sourceId, restartEpoch, outcome.orElse(null));
    if (outcome.isEmpty()) {
      return SourceReconciliationResult.none();
    }

    var started =
        outcome.get() == ConnectionManager.StartOutcome.STARTED
                || outcome.get() == ConnectionManager.StartOutcome.RESTARTED
            ? 1
            : 0;
    return new SourceReconciliationResult(started, 1, 0, 0);
  }

  private SourceReconciliationResult restartConnectedFromDesiredState(DesiredSocketSource desired) {
    var sourceId = desired.id();
    var outcome = lifecycleService.restartFromDesired(desired);
    clearRecoveryAttempt(sourceId);
    if (outcome.isEmpty()) {
      return SourceReconciliationResult.none();
    }

    var started =
        outcome.get() == ConnectionManager.StartOutcome.STARTED
                || outcome.get() == ConnectionManager.StartOutcome.RESTARTED
            ? 1
            : 0;
    return new SourceReconciliationResult(started, 1, 1, 0);
  }

  private SourceReconciliationResult recoverTerminalRuntime(DesiredSocketSource desired) {
    var sourceId = desired.id();
    var outcome = lifecycleService.ensureStarted(desired);
    if (outcome == ConnectionManager.StartOutcome.STARTED
        || outcome == ConnectionManager.StartOutcome.RESTARTED) {
      return SourceReconciliationResult.started();
    }

    if (!keepsRecoveryBackoff(outcome)) {
      clearRecoveryAttempt(sourceId);
    }
    return SourceReconciliationResult.none();
  }

  private ReconciliationResult handleDesiredStateReadFailure(
      List<RuntimeStatus> localRuntime, Exception e) {
    log.warn(
        "Desired ownership reconciliation for {} aborted because {} read failed: {}",
        instanceId,
        desiredRepository.diagnosticLabel(),
        e.getMessage());

    if (!outagePolicy.stopOnDesiredStateUnavailable()) {
      return ReconciliationResult.failedResult(localRuntime.size());
    }

    var now = clock.instant();
    if (desiredStateOutageStartedAt == null) {
      desiredStateOutageStartedAt = now;
    }

    var stopAt = desiredStateOutageStartedAt.plus(outagePolicy.desiredStateUnavailableGrace());
    if (now.isBefore(stopAt)) {
      log.warn(
          "Desired-state outage for {} has not exceeded grace {}; keeping {} local sources",
          instanceId,
          outagePolicy.desiredStateUnavailableGrace(),
          localRuntime.size());
      return ReconciliationResult.failedResult(localRuntime.size());
    }

    var stopped = 0;
    for (var runtime : localRuntime) {
      nextRecoveryAttemptAt.remove(runtime.id());
      if (lifecycleService.ensureStopped(runtime.id())) {
        stopped++;
      }
    }
    log.warn(
        "Desired-state outage for {} exceeded grace {}; stopped {} local sources",
        instanceId,
        outagePolicy.desiredStateUnavailableGrace(),
        stopped);
    return ReconciliationResult.failedResult(localRuntime.size(), stopped);
  }

  private void logLifecycleFailure(UUID sourceId, String actionToLog, Exception e) {
    var message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    log.warn(
        "Desired ownership reconciliation for {} failed to {} source {}: {}",
        instanceId,
        actionToLog,
        sourceId,
        message);
  }

  private Map<UUID, Long> findRestartEpochs(
      List<DesiredSocketSource> desiredSources, List<RuntimeStatus> localRuntime) {
    if (restartEpochReader == null) {
      return Map.of();
    }

    var ids = sourceIds(localRuntime);
    for (var desired : desiredSources) {
      ids.add(desired.id());
    }
    return restartEpochReader.findRestartEpochs(ids);
  }

  private boolean hasUnappliedRestartEpoch(Map<UUID, Long> restartEpochs) {
    for (var entry : restartEpochs.entrySet()) {
      if (lifecycleService.hasUnappliedRestartEpoch(entry.getKey(), entry.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static Set<UUID> sourceIds(List<RuntimeStatus> runtime) {
    var ids = new HashSet<UUID>();
    for (var status : runtime) {
      ids.add(status.id());
    }
    return ids;
  }

  private static boolean shouldRecover(ConnectorState state) {
    return state == ConnectorState.STOPPED
        || state == ConnectorState.FAILED
        || state == ConnectorState.DISCONNECTED;
  }

  private boolean claimRecoveryAttempt(UUID sourceId, ConnectorState state, long restartEpoch) {
    if (recoveryMinInterval.isZero()) {
      return true;
    }

    var now = clock.instant();
    var recoveryBackoff = nextRecoveryAttemptAt.get(sourceId);
    if (recoveryBackoff != null && now.isBefore(recoveryBackoff.nextAttemptAt())) {
      log.debug(
          "Skipping recovery for source {} in {} state until {}",
          sourceId,
          state,
          recoveryBackoff.nextAttemptAt());
      return false;
    }

    var scheduledAttemptAt = now.plus(recoveryMinInterval);
    nextRecoveryAttemptAt.put(
        sourceId, new RecoveryBackoff(scheduledAttemptAt, Math.max(0L, restartEpoch)));
    log.info(
        "Recovering source {} in {} state; next terminal recovery attempt allowed after {}",
        sourceId,
        state,
        scheduledAttemptAt);
    return true;
  }

  private boolean recoveryBackoffActive(
      UUID sourceId, long restartEpoch, boolean hasUnappliedRestartEpoch) {
    if (recoveryMinInterval.isZero()) {
      return false;
    }

    var recoveryBackoff = nextRecoveryAttemptAt.get(sourceId);
    if (recoveryBackoff == null) {
      return false;
    }

    if (hasUnappliedRestartEpoch && restartEpoch > recoveryBackoff.restartEpochAtBackoff()) {
      return false;
    }

    var now = clock.instant();
    if (!now.isBefore(recoveryBackoff.nextAttemptAt())) {
      return false;
    }

    log.debug(
        "Skipping lifecycle start/restart for source {} until {}",
        sourceId,
        recoveryBackoff.nextAttemptAt());
    return true;
  }

  private void scheduleNextRecoveryAttempt(UUID sourceId, long restartEpoch) {
    if (recoveryMinInterval.isZero()) {
      return;
    }

    var scheduledAttemptAt = clock.instant().plus(recoveryMinInterval);
    nextRecoveryAttemptAt.put(
        sourceId, new RecoveryBackoff(scheduledAttemptAt, Math.max(0L, restartEpoch)));
  }

  /**
   * An unavailable integration stays unavailable for this process's lifetime, so its next attempt
   * waits for the recovery backoff rather than running on every tick.
   */
  private static boolean keepsRecoveryBackoff(ConnectionManager.StartOutcome outcome) {
    return outcome == ConnectionManager.StartOutcome.INTEGRATION_UNAVAILABLE;
  }

  /** Backs off after an attempt that must not be retried at once; otherwise forgets the attempt. */
  private void settleRecoveryAttempt(
      UUID sourceId, long restartEpoch, ConnectionManager.StartOutcome outcome) {
    if (keepsRecoveryBackoff(outcome)) {
      scheduleNextRecoveryAttempt(sourceId, restartEpoch);
    } else {
      clearRecoveryAttempt(sourceId);
    }
  }

  private void clearRecoveryAttempt(UUID sourceId) {
    nextRecoveryAttemptAt.remove(sourceId);
  }
}
