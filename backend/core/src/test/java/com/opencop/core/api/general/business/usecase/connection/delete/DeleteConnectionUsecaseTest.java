// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.delete;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.service.ConnectionArchiveLifecycleService;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.extension.ConnectionDeleteHooks;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.api.general.technical.gateway.kafka.ByodSourcePublisher;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.MessageBrokerProvider;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DeleteConnectionUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440211");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440311");
  private static final String GENERATION =
      "opencop/shared/connect/" + SOURCE_ID + "/7d0b8f6e-1c2a-4b3d-9e4f-5a6b7c8d9e0f";
  private static final String CLIENT_REF = GENERATION + "/client";
  private static final String TRUSTSTORE_REF = GENERATION + "/truststore";
  private static final String NEXT_GENERATION =
      "opencop/shared/connect/" + SOURCE_ID + "/2e9c4a1b-6d3f-4c8e-a7b5-0f1e2d3c4b5a";
  private static final String NEXT_CLIENT_REF = NEXT_GENERATION + "/client";
  private static final String NEXT_TRUSTSTORE_REF = NEXT_GENERATION + "/truststore";
  private static final String TOPIC = "topic-source-1";
  private static final String USERNAME = "user-source-1";
  private static final CurrentBrokerProvider CURRENT_BROKER_PROVIDER =
      new CurrentBrokerProvider(MessageBrokerProvider.LOCAL.name());

  /** Collaborators for one usecase under test, so each case only names what it asserts on. */
  private record Fixture(
      ConnectionAuthorization authorization,
      ServerManager<Server> serverManager,
      SocketReconciliationNudgePublisher nudgePublisher,
      DataSourceRepository dataSourceRepository,
      ConnectionStore connectionStore,
      EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourcePublisher sourcePublisher,
      ConnectionDeleteHooks deleteHooks,
      Runnable afterDeletion,
      ConnectionMetadataGateway connections,
      AuditRecorder auditRecorder,
      PlatformTransactionManager transactionManager) {

    @SuppressWarnings("unchecked")
    static Fixture create() {
      return new Fixture(
          mock(ConnectionAuthorization.class),
          mock(ServerManager.class),
          mock(SocketReconciliationNudgePublisher.class),
          mock(DataSourceRepository.class),
          mock(ConnectionStore.class),
          mock(EventGeoCacheEvictionGateway.class),
          mock(KafkaProvisioningService.class),
          mock(ByodSourcePublisher.class),
          mock(ConnectionDeleteHooks.class),
          mock(Runnable.class),
          mock(ConnectionMetadataGateway.class),
          mock(AuditRecorder.class),
          mock(PlatformTransactionManager.class));
    }

    DeleteConnectionUsecase usecase() {
      var archiveLifecycle =
          new ConnectionArchiveLifecycleService(
              nudgePublisher,
              CURRENT_BROKER_PROVIDER,
              dataSourceRepository,
              connectionStore,
              eventGeoCacheEvictionGateway,
              kafkaProvisioner,
              sourcePublisher,
              auditRecorder,
              new DataSourceAuditProjection(),
              transactionManager);
      when(deleteHooks.prepare(SOURCE_ID, USER_ID)).thenReturn(afterDeletion);
      return new DeleteConnectionUsecase(
          authorization,
          serverManager,
          archiveLifecycle,
          deleteHooks,
          new TransactionalConnectionLock(
              connections, new TransactionTemplate(transactionManager)));
    }

    void withSource(DataSource dataSource) {
      when(dataSourceRepository.findByIdForProvider(SOURCE_ID, CURRENT_BROKER_PROVIDER.name()))
          .thenReturn(Optional.of(dataSource));
    }
  }

  @Test
  void delete_whenCallerIsNotAuthorized_stopsBeforeEveryArchiveSideEffect() {
    var f = Fixture.create();
    var principal = principal(false);
    doThrow(new AccessDeniedException("denied"))
        .when(f.authorization())
        .assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);

    var usecase = f.usecase();

    assertThrows(AccessDeniedException.class, () -> usecase.delete(SOURCE_ID, principal));

    verify(f.authorization()).assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);
    // The access lookup must not run for an unauthorized caller: it is what used to reveal whether
    // an unknown source exists before authorization was checked.
    verifyNoInteractions(
        f.serverManager(),
        f.dataSourceRepository(),
        f.connectionStore(),
        f.eventGeoCacheEvictionGateway(),
        f.kafkaProvisioner(),
        f.sourcePublisher(),
        f.nudgePublisher(),
        f.connections());
    verify(f.deleteHooks(), never()).prepare(SOURCE_ID, USER_ID);
  }

  @Test
  void delete_preparesTheExtensionsHooksBeforeArchivingAndRunsThemOnceArchived() {
    var f = Fixture.create();
    f.withSource(socketSource());

    f.usecase().delete(SOURCE_ID, principal(false));

    var sequence =
        inOrder(f.deleteHooks(), f.connectionStore(), f.dataSourceRepository(), f.afterDeletion());
    sequence.verify(f.deleteHooks()).prepare(SOURCE_ID, USER_ID);
    sequence.verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.afterDeletion()).run();
  }

  /**
   * The row lock is held from the hooks' read to the archive, so a setup replacing the connection's
   * in between waits and is then refused: the hooks read the setup that is archived.
   */
  /**
   * The archive and its row commit together, so a reader never sees a source vanish with no
   * history. The row moves no field; it carries the transport the source ran on.
   */
  @Test
  void delete_recordsTheArchiveInTheSameTransactionAsTheArchiveItself() {
    var f = Fixture.create();
    f.withSource(socketSource());

    f.usecase().delete(SOURCE_ID, principal(false));

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    var sequence = inOrder(f.dataSourceRepository(), f.auditRecorder(), f.transactionManager());
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.auditRecorder()).record(recorded.capture());
    sequence.verify(f.transactionManager()).commit(any());

    var event = recorded.getValue();
    assertEquals(EntityType.DATA_SOURCE, event.entityType());
    assertEquals(SOURCE_ID, event.entityId());
    assertEquals(Operation.ARCHIVE, event.operation());
    assertEquals(USER_ID, event.actor().userId());
    assertTrue(event.change().changed().isEmpty());
    assertEquals(TransportKind.TCP.name(), event.change().details().get("transportKind"));
  }

  @Test
  void delete_holdsTheConnectionsLockFromTheHooksReadToTheArchive() {
    var f = Fixture.create();
    f.withSource(socketSource());

    f.usecase().delete(SOURCE_ID, principal(false));

    var sequence =
        inOrder(
            f.transactionManager(),
            f.connections(),
            f.deleteHooks(),
            f.dataSourceRepository(),
            f.afterDeletion());
    sequence.verify(f.transactionManager()).getTransaction(any());
    sequence.verify(f.connections()).lockById(SOURCE_ID);
    sequence.verify(f.deleteHooks()).prepare(SOURCE_ID, USER_ID);
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.transactionManager()).commit(any());
    sequence.verify(f.afterDeletion()).run();
  }

  @Test
  void delete_whenCallerIsGlobalAdmin_passesAdminFlagToAuthorization() {
    var f = Fixture.create();
    f.withSource(socketSource());

    f.usecase().delete(SOURCE_ID, principal(true));

    verify(f.authorization()).assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, true);
  }

  @Test
  void delete_returnsTheUsersWhoHadAccessSoTheCallerCanNotifyThem() {
    var f = Fixture.create();
    f.withSource(socketSource());
    var affected = Set.of(mock(SourceUser.class), mock(SourceUser.class));
    when(f.serverManager().getUsers(SOURCE_ID)).thenReturn(affected);

    var returned = f.usecase().delete(SOURCE_ID, principal(false));

    assertEquals(affected, returned);
    // Authorization first, then the archive, and only then the access lookup — so an unknown source
    // is reported as not found instead of by the lookup's own existence check.
    var sequence = inOrder(f.authorization(), f.dataSourceRepository(), f.serverManager());
    sequence.verify(f.authorization()).assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.serverManager()).getUsers(SOURCE_ID);
  }

  @Test
  void delete_whenKafkaSource_evictsCacheBeforePublishingDeletedAndDeprovisionsAfterArchive() {
    var f = Fixture.create();
    var kafkaSource = kafkaSource();
    when(kafkaSource.topicName()).thenReturn(TOPIC);
    when(kafkaSource.username()).thenReturn(USERNAME);
    f.withSource(kafkaSource);

    f.usecase().delete(SOURCE_ID, principal(false));

    var sequence =
        inOrder(
            f.eventGeoCacheEvictionGateway(),
            f.sourcePublisher(),
            f.dataSourceRepository(),
            f.kafkaProvisioner());
    sequence.verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    sequence
        .verify(f.sourcePublisher())
        .publish(
            argThat(
                message ->
                    message.sourceId().equals(SOURCE_ID)
                        && message.status() == ByodSourceState.Status.DELETED
                        && message.topicName().equals(TOPIC)
                        && message.username().equals(USERNAME)));
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.kafkaProvisioner()).deprovision(TOPIC, USERNAME, SOURCE_ID);
    verifyNoInteractions(f.nudgePublisher(), f.connectionStore());
  }

  @Test
  void delete_whenSocketSource_evictsCacheBeforeClearingSecretsAndRequestsReconciliation() {
    var f = Fixture.create();
    f.withSource(socketSource());

    f.usecase().delete(SOURCE_ID, principal(false));

    var sequence =
        inOrder(
            f.eventGeoCacheEvictionGateway(),
            f.connectionStore(),
            f.dataSourceRepository(),
            f.nudgePublisher());
    sequence.verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    sequence.verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    sequence.verify(f.connectionStore()).deleteLegacy(SOURCE_ID);
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.nudgePublisher()).requestReconciliation();
    verifyNoMoreInteractions(f.nudgePublisher());
    verifyNoInteractions(f.kafkaProvisioner(), f.sourcePublisher());
  }

  @Test
  void delete_whenASetupCommitsDuringTheArchive_deletesTheSecretsItCommitted() {
    var f = Fixture.create();
    f.withSource(socketSource());
    var archived = socketSource(NEXT_CLIENT_REF, NEXT_TRUSTSTORE_REF);
    when(f.dataSourceRepository().findById(SOURCE_ID)).thenReturn(Optional.of(archived));

    f.usecase().delete(SOURCE_ID, principal(false));

    var sequence = inOrder(f.connectionStore(), f.dataSourceRepository(), f.nudgePublisher());
    sequence.verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    sequence.verify(f.connectionStore()).delete(List.of(NEXT_CLIENT_REF, NEXT_TRUSTSTORE_REF));
    sequence.verify(f.nudgePublisher()).requestReconciliation();
  }

  @Test
  void delete_whenNoSetupCommitsDuringTheArchive_deletesEachSecretOnce() {
    var f = Fixture.create();
    var source = socketSource();
    f.withSource(source);
    when(f.dataSourceRepository().findById(SOURCE_ID)).thenReturn(Optional.of(source));

    f.usecase().delete(SOURCE_ID, principal(false));

    verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    verify(f.connectionStore()).deleteLegacy(SOURCE_ID);
    verifyNoMoreInteractions(f.connectionStore());
  }

  @Test
  void delete_whenSecretsCommittedDuringTheArchiveFailToDelete_stillCompletesTheArchive() {
    var f = Fixture.create();
    f.withSource(socketSource());
    var archived = socketSource(NEXT_CLIENT_REF, NEXT_TRUSTSTORE_REF);
    when(f.dataSourceRepository().findById(SOURCE_ID)).thenReturn(Optional.of(archived));
    doThrow(new IllegalStateException("secret cleanup failed"))
        .when(f.connectionStore())
        .delete(List.of(NEXT_CLIENT_REF, NEXT_TRUSTSTORE_REF));

    f.usecase().delete(SOURCE_ID, principal(false));

    verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    verify(f.nudgePublisher()).requestReconciliation();
  }

  @Test
  void delete_whenSocketSecretCleanupFails_doesNotArchive() {
    var f = Fixture.create();
    f.withSource(socketSource());
    doThrow(new IllegalStateException("secret cleanup failed"))
        .when(f.connectionStore())
        .delete(List.of(CLIENT_REF, TRUSTSTORE_REF));

    var usecase = f.usecase();

    assertThrows(IllegalStateException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    var sequence = inOrder(f.eventGeoCacheEvictionGateway(), f.connectionStore());
    sequence.verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    sequence.verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    verifyNoInteractions(f.kafkaProvisioner(), f.sourcePublisher(), f.nudgePublisher());
    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verify(f.afterDeletion(), never()).run();
  }

  @Test
  void delete_whenSourceIsMissing_returnsNotFoundWithoutSideEffects() {
    var f = Fixture.create();
    when(f.dataSourceRepository().findByIdForProvider(SOURCE_ID, CURRENT_BROKER_PROVIDER.name()))
        .thenReturn(Optional.empty());

    var usecase = f.usecase();

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verifyNoInteractions(
        f.nudgePublisher(),
        f.connectionStore(),
        f.kafkaProvisioner(),
        f.sourcePublisher(),
        f.eventGeoCacheEvictionGateway());
  }

  @Test
  void delete_whenSourceBelongsToAnotherBrokerProvider_returnsNotFoundWithoutSideEffects() {
    var f = Fixture.create();
    when(f.dataSourceRepository().findByIdForProvider(SOURCE_ID, CURRENT_BROKER_PROVIDER.name()))
        .thenReturn(Optional.empty());

    var usecase = f.usecase();

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    assertEquals(HttpStatus.NOT_FOUND, error.getHttpStatus());
    verify(f.dataSourceRepository()).findByIdForProvider(SOURCE_ID, CURRENT_BROKER_PROVIDER.name());
    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verifyNoInteractions(
        f.nudgePublisher(),
        f.connectionStore(),
        f.kafkaProvisioner(),
        f.sourcePublisher(),
        f.eventGeoCacheEvictionGateway());
  }

  @Test
  void delete_whenSourceIsAlreadyArchived_returnsNotFoundWithoutSideEffects() {
    var f = Fixture.create();
    var socketSource = socketSource();
    when(socketSource.archivedAt()).thenReturn(Instant.parse("2026-05-27T12:00:00Z"));
    f.withSource(socketSource);

    var usecase = f.usecase();

    assertThrows(GlobalOpenCopException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verifyNoInteractions(
        f.nudgePublisher(),
        f.connectionStore(),
        f.kafkaProvisioner(),
        f.sourcePublisher(),
        f.eventGeoCacheEvictionGateway());
  }

  @Test
  void delete_whenArchiveFails_doesNotRunPostArchiveSideEffects() {
    var f = Fixture.create();
    f.withSource(socketSource());
    doThrow(new IllegalStateException("archive failed"))
        .when(f.dataSourceRepository())
        .archive(SOURCE_ID, USER_ID);

    var usecase = f.usecase();

    assertThrows(IllegalStateException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    var sequence =
        inOrder(f.eventGeoCacheEvictionGateway(), f.connectionStore(), f.dataSourceRepository());
    sequence.verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    sequence.verify(f.connectionStore()).delete(List.of(CLIENT_REF, TRUSTSTORE_REF));
    sequence.verify(f.dataSourceRepository()).archive(SOURCE_ID, USER_ID);
    verifyNoInteractions(f.kafkaProvisioner(), f.sourcePublisher(), f.nudgePublisher());
    // Twice, on the one manager production injects everywhere: the lock's transaction and the
    // archive's own, which carries the ARCHIVE audit row. A failed archive must take that row
    // with it, so neither may commit.
    verify(f.transactionManager(), times(2)).rollback(any());
    verify(f.afterDeletion(), never()).run();
  }

  @Test
  void delete_whenEvictionFailsForSocketSource_leavesTheSourceUnarchivedAndRetryable() {
    var f = Fixture.create();
    f.withSource(socketSource());
    doThrow(new IllegalStateException("redis eviction failed"))
        .when(f.eventGeoCacheEvictionGateway())
        .evictGeoSource(SOURCE_ID);

    var usecase = f.usecase();

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getHttpStatus());
    // Eviction spans several Redis commands, so a failure cannot promise the cache is intact. The
    // message may only offer an unarchived, retryable source, never an untouched one.
    assertEquals(
        "Cached events could not be evicted. The data source was not archived so the delete can be"
            + " retried, but some of its cached events may already have been removed.",
        error.getMessage());
    verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verifyNoInteractions(
        f.connectionStore(), f.kafkaProvisioner(), f.sourcePublisher(), f.nudgePublisher());
  }

  /**
   * A failed eviction must not reach the DELETED publish. The Processor treats DELETED as terminal,
   * so publishing it and then failing would stop the consumer for a source that still exists, with
   * no later state able to restart it.
   */
  @Test
  void delete_whenEvictionFailsForKafkaSource_neverPublishesTheTerminalDeletedState() {
    var f = Fixture.create();
    f.withSource(kafkaSource());
    doThrow(new IllegalStateException("redis eviction failed"))
        .when(f.eventGeoCacheEvictionGateway())
        .evictGeoSource(SOURCE_ID);

    var usecase = f.usecase();

    var error =
        assertThrows(
            GlobalOpenCopException.class, () -> usecase.delete(SOURCE_ID, principal(false)));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getHttpStatus());
    verify(f.eventGeoCacheEvictionGateway()).evictGeoSource(SOURCE_ID);
    verify(f.dataSourceRepository(), never()).archive(SOURCE_ID, USER_ID);
    verify(f.sourcePublisher(), never()).publish(any());
    verifyNoInteractions(f.connectionStore(), f.kafkaProvisioner(), f.nudgePublisher());
  }

  /** A socket source whose spec references the secrets of one mTLS provisioning. */
  private static KafkaDataSource kafkaSource() {
    var source = mock(KafkaDataSource.class);
    when(source.provider()).thenReturn(TransportKind.KAFKA);
    return source;
  }

  private static SocketDataSource socketSource() {
    return socketSource(CLIENT_REF, TRUSTSTORE_REF);
  }

  private static SocketDataSource socketSource(String clientRef, String truststoreRef) {
    var source = mock(SocketDataSource.class);
    when(source.provider()).thenReturn(TransportKind.TCP);
    when(source.spec())
        .thenReturn(
            new ConnectionSpec(
                new TcpConfig(
                    "127.0.0.1",
                    8087,
                    null,
                    null,
                    null,
                    null,
                    new MtlsConfig(
                        null,
                        null,
                        false,
                        new KeystoreRef(truststoreRef, "PKCS12"),
                        new KeystoreRef(clientRef, "PKCS12")))));
    return source;
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    return TestPrincipals.oidcPrincipal(USER_ID, globalAdmin);
  }
}
