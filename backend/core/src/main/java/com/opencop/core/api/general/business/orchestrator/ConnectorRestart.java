// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.orchestrator;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restarts a source's connector on Connect, in two steps around its caller's transaction.
 *
 * <p>{@link #request} advances the source's durable restart epoch. It writes through the caller's
 * transaction when there is one, so a caller that rolls back also takes the restart back. A caller
 * with a transaction reads the {@link #baseline} before opening it: that read is best-effort, and a
 * failed statement would abort the caller's transaction. {@link #applyAndObserve} then wakes
 * Connect, once whatever requested the restart has committed, and waits briefly to observe the
 * connector that replaces the running one. Connect rereads desired ownership before applying the
 * bounce, so the restart applies what was committed with it.
 */
public class ConnectorRestart {

  private static final Logger log = LoggerFactory.getLogger(ConnectorRestart.class);
  private static final Duration DEFAULT_OBSERVATION_WAIT = Duration.ofSeconds(3);
  private static final Duration DEFAULT_OBSERVATION_POLL = Duration.ofMillis(200);

  private final SourceRestartEpochWriter restartEpochWriter;
  private final SocketReconciliationNudgePublisher nudgePublisher;
  @Nullable private final SourceRuntimeObservationReader sourceRuntimeObservationReader;
  private final Duration observationWait;
  private final Duration observationPoll;

  public ConnectorRestart(
      SourceRestartEpochWriter restartEpochWriter,
      SocketReconciliationNudgePublisher nudgePublisher,
      @Nullable SourceRuntimeObservationReader sourceRuntimeObservationReader) {
    this(
        restartEpochWriter,
        nudgePublisher,
        sourceRuntimeObservationReader,
        DEFAULT_OBSERVATION_WAIT,
        DEFAULT_OBSERVATION_POLL);
  }

  public ConnectorRestart(
      SourceRestartEpochWriter restartEpochWriter,
      SocketReconciliationNudgePublisher nudgePublisher,
      @Nullable SourceRuntimeObservationReader sourceRuntimeObservationReader,
      Duration observationWait,
      Duration observationPoll) {
    this.restartEpochWriter = restartEpochWriter;
    this.nudgePublisher = nudgePublisher;
    this.sourceRuntimeObservationReader = sourceRuntimeObservationReader;
    this.observationWait = observationWait;
    this.observationPoll = observationPoll;
  }

  /**
   * Advances the source's restart epoch outside any transaction. The running connector generation
   * is read first, so the one that replaces it can be told apart.
   *
   * @param instanceId the Connect instance that should run the source
   */
  public PendingRestart request(UUID sourceId, String instanceId) {
    return request(sourceId, baseline(sourceId, instanceId), instanceId);
  }

  /**
   * Advances the source's restart epoch, within the caller's transaction when there is one. A
   * baseline read for another instance than the one that should run the source tells nothing about
   * its connector, so any connector started since the baseline then counts as the replacement.
   *
   * @param baseline the connector running before the restart, read before the caller's transaction
   * @param instanceId the Connect instance that should run the source
   */
  public PendingRestart request(UUID sourceId, RestartBaseline baseline, String instanceId) {
    var previousConnectorStartedAt =
        Objects.equals(baseline.instanceId(), instanceId) ? baseline.connectorStartedAt() : null;
    var epoch = restartEpochWriter.incrementRestartEpoch(sourceId);
    return new PendingRestart(
        sourceId, instanceId, epoch, previousConnectorStartedAt, baseline.observedAt());
  }

  /**
   * The connector generation running on an instance now, best-effort. Read it outside any
   * transaction: a failed read is tolerated, which a transaction it joined would not survive.
   *
   * @param instanceId the Connect instance running the source, or null when none is assigned
   */
  public RestartBaseline baseline(UUID sourceId, @Nullable String instanceId) {
    var observedAt = Instant.now();
    var connectorStartedAt =
        instanceId == null ? null : findConnectorStartedAt(sourceId, instanceId);
    return new RestartBaseline(instanceId, connectorStartedAt, observedAt);
  }

  /**
   * Wakes Connect, best-effort, then waits briefly for a fresh connector generation. Call it once
   * the restart is committed. A wake-up that fails or a wait that times out still leaves the
   * restart to the next periodic reconciliation.
   */
  public void applyAndObserve(PendingRestart restart) {
    try {
      nudgePublisher.requestReconciliation();
    } catch (Exception e) {
      log.warn(
          "Restart epoch {} for source {} was persisted, but Postgres reconciliation nudge failed: {}",
          restart.epoch(),
          restart.sourceId(),
          e.getMessage(),
          e);
    }
    log.info(
        "Restart epoch {} accepted for source {} on desired Connect instance {}; Postgres reconciliation nudge is best-effort",
        restart.epoch(),
        restart.sourceId(),
        restart.instanceId());
    waitForFreshConnectorGeneration(restart);
  }

  private Instant findConnectorStartedAt(UUID sourceId, String instanceId) {
    if (sourceRuntimeObservationReader == null) {
      return null;
    }
    try {
      return sourceRuntimeObservationReader
          .findFreshForInstance(sourceId, instanceId)
          .map(RuntimeStatus::connectorStartedAt)
          .orElse(null);
    } catch (Exception e) {
      log.debug(
          "Postgres read failed for restart observation baseline {} on {}: {}",
          sourceId,
          instanceId,
          e.getMessage());
      return null;
    }
  }

  private void waitForFreshConnectorGeneration(PendingRestart restart) {
    if (!observesRestarts()) {
      return;
    }

    var sourceId = restart.sourceId();
    var instanceId = restart.instanceId();
    var deadline = Instant.now().plus(observationWait);
    while (Instant.now().isBefore(deadline)) {
      try {
        var observed =
            sourceRuntimeObservationReader
                .findFreshForInstance(sourceId, instanceId)
                .map(RuntimeStatus::connectorStartedAt)
                .orElse(null);
        if (restart.isReplacedBy(observed)) {
          log.info(
              "Observed fresh connector generation for restart of source {} on {} in {} ms",
              sourceId,
              instanceId,
              Duration.between(restart.requestedAt(), Instant.now()).toMillis());
          return;
        }
        Thread.sleep(observationPoll.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        log.debug(
            "Postgres read failed while waiting for restart observation {} on {}: {}",
            sourceId,
            instanceId,
            e.getMessage());
        return;
      }
    }
    log.info(
        "Timed out after {} ms waiting for fresh connector generation for restart of source {} on {}; restart remains accepted and will be applied by the next periodic reconciliation tick if Connect did not already apply it",
        observationWait.toMillis(),
        sourceId,
        instanceId);
  }

  /** Without runtime observations, or without time to wait for them, a restart is not observed. */
  private boolean observesRestarts() {
    return sourceRuntimeObservationReader != null
        && observationWait != null
        && observationWait.isPositive();
  }

  /**
   * The connector a restart is told apart from.
   *
   * @param instanceId the instance the baseline was read on, or null when none was assigned
   * @param connectorStartedAt when its running connector started, or null when none was observed
   * @param observedAt when the baseline was read
   */
  public record RestartBaseline(
      @Nullable String instanceId, @Nullable Instant connectorStartedAt, Instant observedAt) {}

  /**
   * A restart requested but not yet observed.
   *
   * @param epoch the restart epoch the request advanced the source to
   * @param previousConnectorStartedAt when the connector running at the request started, or null
   *     when none was observed
   * @param requestedAt when the restart was requested, or its baseline read just before
   */
  public record PendingRestart(
      UUID sourceId,
      String instanceId,
      long epoch,
      @Nullable Instant previousConnectorStartedAt,
      Instant requestedAt) {

    /**
     * Whether a connector that started at {@code observed} replaced the one running at the request.
     * Without a running one to compare against, any connector started since the request did.
     */
    boolean isReplacedBy(@Nullable Instant observed) {
      if (observed == null) {
        return false;
      }
      if (previousConnectorStartedAt != null) {
        return observed.isAfter(previousConnectorStartedAt);
      }
      return !observed.isBefore(requestedAt);
    }
  }
}
