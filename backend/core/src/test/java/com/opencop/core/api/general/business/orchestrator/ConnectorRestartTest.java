// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorRestartTest {

  private static final UUID SOURCE_ID = UUID.fromString("5d2f3a6e-8b1c-4e7d-9a0f-1b2c3d4e5f60");
  private static final String INSTANCE_ID = "connect-1";
  private static final Instant PREVIOUS_GENERATION = Instant.parse("2026-05-04T12:00:00Z");
  private static final Duration WAIT = Duration.ofSeconds(2);
  private static final Duration POLL = Duration.ofMillis(1);

  private final SourceRestartEpochWriter epochWriter = mock(SourceRestartEpochWriter.class);
  private final SocketReconciliationNudgePublisher nudgePublisher =
      mock(SocketReconciliationNudgePublisher.class);
  private final SourceRuntimeObservationReader observations =
      mock(SourceRuntimeObservationReader.class);

  /** Reading the baseline after the bump could already see the new generation as the old one. */
  @Test
  void capturesTheRunningGenerationBeforeBumpingTheEpoch() {
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION)));
    when(epochWriter.incrementRestartEpoch(SOURCE_ID)).thenReturn(4L);

    var pending = restart(WAIT).request(SOURCE_ID, INSTANCE_ID);

    var order = inOrder(observations, epochWriter);
    order.verify(observations).findFreshForInstance(SOURCE_ID, INSTANCE_ID);
    order.verify(epochWriter).incrementRestartEpoch(SOURCE_ID);
    assertThat(pending.epoch()).isEqualTo(4L);
    assertThat(pending.previousConnectorStartedAt()).isEqualTo(PREVIOUS_GENERATION);
  }

  @Test
  void requestsFromABaselineReadEarlierOnTheSameInstance() {
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION)));
    var restart = restart(WAIT);
    var baseline = restart.baseline(SOURCE_ID, INSTANCE_ID);

    var pending = restart.request(SOURCE_ID, baseline, INSTANCE_ID);

    assertThat(pending.previousConnectorStartedAt()).isEqualTo(PREVIOUS_GENERATION);
    assertThat(pending.requestedAt()).isEqualTo(baseline.observedAt());
  }

  /** A baseline read on the instance a connection left says nothing about where it runs now. */
  @Test
  void discardsABaselineReadOnAnotherInstance() {
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION)));
    var restart = restart(WAIT);

    var pending = restart.request(SOURCE_ID, restart.baseline(SOURCE_ID, INSTANCE_ID), "connect-2");

    assertThat(pending.instanceId()).isEqualTo("connect-2");
    assertThat(pending.previousConnectorStartedAt()).isNull();
  }

  @Test
  void readsNoBaselineForAConnectionAssignedToNoInstance() {
    var baseline = restart(WAIT).baseline(SOURCE_ID, null);

    assertThat(baseline.connectorStartedAt()).isNull();
    verify(observations, never()).findFreshForInstance(SOURCE_ID, null);
  }

  /** The baseline is best-effort: a failed read leaves the restart to be told apart by time. */
  @Test
  void toleratesAFailedBaselineRead() {
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenThrow(new IllegalStateException("observations unavailable"));

    var baseline = restart(WAIT).baseline(SOURCE_ID, INSTANCE_ID);

    assertThat(baseline.connectorStartedAt()).isNull();
  }

  /** Requesting only writes: Connect is woken once the caller's transaction committed. */
  @Test
  void requestingARestartDoesNotWakeConnect() {
    restart(WAIT).request(SOURCE_ID, INSTANCE_ID);

    verify(nudgePublisher, never()).requestReconciliation();
  }

  @Test
  void wakesConnectThenWaitsForAFreshConnectorGeneration() {
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION)))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION)))
        .thenReturn(Optional.of(runtime(PREVIOUS_GENERATION.plusSeconds(5))));
    var restart = restart(WAIT);
    var pending = restart.request(SOURCE_ID, INSTANCE_ID);

    restart.applyAndObserve(pending);

    verify(nudgePublisher).requestReconciliation();
    verify(observations, times(3)).findFreshForInstance(SOURCE_ID, INSTANCE_ID);
  }

  @Test
  void stillWaitsWhenWakingConnectFails() {
    doThrow(new IllegalStateException("notify failed"))
        .when(nudgePublisher)
        .requestReconciliation();
    when(observations.findFreshForInstance(SOURCE_ID, INSTANCE_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(runtime(Instant.now().plusSeconds(1))));
    var restart = restart(WAIT);
    var pending = restart.request(SOURCE_ID, INSTANCE_ID);

    assertThatCode(() -> restart.applyAndObserve(pending)).doesNotThrowAnyException();

    verify(observations, times(2)).findFreshForInstance(SOURCE_ID, INSTANCE_ID);
  }

  @Test
  void returnsWithoutWaitingWhenNoObservationReaderIsAvailable() {
    var restart = new ConnectorRestart(epochWriter, nudgePublisher, null, WAIT, POLL);
    var pending = restart.request(SOURCE_ID, INSTANCE_ID);

    restart.applyAndObserve(pending);

    verify(nudgePublisher).requestReconciliation();
    assertThat(pending.previousConnectorStartedAt()).isNull();
  }

  @Test
  void returnsWithoutWaitingWhenTheWaitIsZero() {
    var restart = restart(Duration.ZERO);
    var pending = restart.request(SOURCE_ID, INSTANCE_ID);

    restart.applyAndObserve(pending);

    verify(nudgePublisher).requestReconciliation();
    // Only the baseline read: there is no wait to poll in.
    verify(observations, times(1)).findFreshForInstance(SOURCE_ID, INSTANCE_ID);
  }

  private ConnectorRestart restart(Duration wait) {
    return new ConnectorRestart(epochWriter, nudgePublisher, observations, wait, POLL);
  }

  private static RuntimeStatus runtime(Instant connectorStartedAt) {
    return new RuntimeStatus(
        SOURCE_ID, ConnectorState.CONNECTED, Instant.now(), INSTANCE_ID, connectorStartedAt);
  }
}
