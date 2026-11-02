// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.start.StartConnectionUsecase;
import com.opencop.core.api.general.business.usecase.connection.stop.StopConnectionUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.ByodSourceStatePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Pins what start and stop record and in what order relative to the enabled flag. That the pair
 * commits as one transaction is a property of the real transaction manager, not of these mocks.
 */
class StartStopConnectionAuditTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448002");

  /** The locked read the row is gated on: whether the source was running before the write. */
  private void sourceWasRunning(boolean running) {
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "Feed",
                null,
                TransportKind.KAFKA,
                AccessMode.READ,
                running,
                null,
                true,
                null));
  }

  private final ConnectionAuthorization authorization = mock(ConnectionAuthorization.class);
  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final KafkaProvisioningService provisioner = mock(KafkaProvisioningService.class);
  private final ByodSourceStatePublisher publisher = mock(ByodSourceStatePublisher.class);
  private final AuditRecorder recorder = mock(AuditRecorder.class);
  private final DataSourceAuditProjection projection = new DataSourceAuditProjection();
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);

  private final StopConnectionUsecase stop =
      new StopConnectionUsecase(
          authorization,
          mock(SocketReconciliationNudgePublisher.class),
          dataSources,
          connections,
          new CurrentBrokerProvider("localhost:9092"),
          provisioner,
          publisher,
          recorder,
          projection,
          mock(PlatformTransactionManager.class));

  private final StartConnectionUsecase start =
      new StartConnectionUsecase(
          authorization,
          dataSources,
          mock(ConnectInstanceFailover.class),
          connections,
          new CurrentBrokerProvider("localhost:9092"),
          provisioner,
          publisher,
          recorder,
          projection,
          mock(PlatformTransactionManager.class));

  @Test
  void stop_recordsDisconnectRightAfterTheEnabledFlagAndBeforeTheKafkaWork() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(true);
    when(dataSources.setEnabled(SOURCE_ID, false, USER_ID)).thenReturn(Instant.EPOCH);

    stop.stop(SOURCE_ID, TestActors.admin(USER_ID));

    var order = inOrder(dataSources, recorder, provisioner);
    order.verify(dataSources).setEnabled(SOURCE_ID, false, USER_ID);
    order.verify(recorder).record(any());
    order.verify(provisioner).removeWriteAcl("producer", "source." + SOURCE_ID);
    var event = recordedEvent();
    assertThat(event.entityType()).isEqualTo(EntityType.DATA_SOURCE);
    assertThat(event.entityId()).isEqualTo(SOURCE_ID);
    assertThat(event.operation()).isEqualTo(Operation.DISCONNECT);
    assertThat(event.change().details()).containsEntry("transportKind", TransportKind.KAFKA.name());
  }

  @Test
  void stop_whenTheRowCannotBeWritten_failsTheRequestBeforeAnyKafkaWork() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(true);
    when(dataSources.setEnabled(SOURCE_ID, false, USER_ID)).thenReturn(Instant.EPOCH);
    doThrow(new IllegalStateException("audit down")).when(recorder).record(any());

    assertThatThrownBy(() -> stop.stop(SOURCE_ID, TestActors.admin(USER_ID)))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(provisioner, publisher);
  }

  /**
   * The ledger records what changed. Stopping a source that is already stopped changes nothing, so
   * a second click — or a retry — leaves no second row.
   */
  @Test
  void stop_whenTheSourceWasAlreadyStopped_recordsNoRow() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(false);
    when(dataSources.setEnabled(SOURCE_ID, false, USER_ID)).thenReturn(Instant.EPOCH);

    stop.stop(SOURCE_ID, TestActors.admin(USER_ID));

    verify(dataSources).setEnabled(SOURCE_ID, false, USER_ID);
    verifyNoInteractions(recorder);
  }

  /** Starting a source that is already running is the same no-op from the other direction. */
  @Test
  void start_whenTheSourceWasAlreadyRunning_recordsNoRow() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(true);
    when(dataSources.setEnabled(SOURCE_ID, true, USER_ID)).thenReturn(Instant.EPOCH);

    start.start(SOURCE_ID, TestActors.admin(USER_ID));

    verify(dataSources).setEnabled(SOURCE_ID, true, USER_ID);
    verifyNoInteractions(recorder);
  }

  @Test
  void stop_whenTheSourceIsMissing_recordsNothing() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> stop.stop(SOURCE_ID, TestActors.admin(USER_ID)))
        .isInstanceOf(GlobalOpenCopException.class)
        .extracting(e -> ((GlobalOpenCopException) e).getHttpStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);

    verifyNoInteractions(recorder);
  }

  @Test
  void startKafka_recordsConnectInTheSameUnitAsTheEnabledFlag() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(false);
    when(dataSources.setEnabled(SOURCE_ID, true, USER_ID)).thenReturn(Instant.EPOCH);

    start.start(SOURCE_ID, TestActors.admin(USER_ID));

    var order = inOrder(provisioner, dataSources, recorder, publisher);
    order.verify(provisioner).addWriteAcl("producer", "source." + SOURCE_ID);
    order.verify(dataSources).setEnabled(SOURCE_ID, true, USER_ID);
    order.verify(recorder).record(any());
    order.verify(publisher).publish(any());
    assertThat(recordedEvent().operation()).isEqualTo(Operation.CONNECT);
  }

  @Test
  void startKafka_whenPublishFailsAfterCommit_stillHasItsRow() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));
    sourceWasRunning(false);
    when(dataSources.setEnabled(SOURCE_ID, true, USER_ID)).thenReturn(Instant.EPOCH);
    doThrow(new IllegalStateException("broker down")).when(publisher).publish(any());

    assertThatThrownBy(() -> start.start(SOURCE_ID, TestActors.admin(USER_ID)))
        .isInstanceOf(IllegalStateException.class);

    verify(recorder).record(any());
  }

  private AuditEvent recordedEvent() {
    var captor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(recorder).record(captor.capture());
    return captor.getValue();
  }

  private static KafkaDataSource kafka() {
    return new KafkaDataSource(
        SOURCE_ID,
        "Topic",
        "default",
        "source." + SOURCE_ID,
        "producer",
        null,
        null,
        com.opencop.core.sdk.model.datasource.AccessMode.READ,
        true,
        null,
        null,
        Instant.EPOCH,
        USER_ID,
        Instant.EPOCH,
        USER_ID,
        null,
        "localhost:9092",
        true);
  }
}
