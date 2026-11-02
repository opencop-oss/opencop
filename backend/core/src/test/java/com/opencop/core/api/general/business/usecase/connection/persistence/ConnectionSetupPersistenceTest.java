// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart.PendingRestart;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart.RestartBaseline;
import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.extension.ExtensionSecretEntries;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

class ConnectionSetupPersistenceTest {

  private static final UUID SOURCE_ID = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e");
  private static final UUID USER_ID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f");
  private static final UUID MAPPER_ID = UUID.fromString("4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f80");
  private static final UUID EXTRACTOR_ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-4c5d6e7f8091");
  private static final int LISTEN_PORT = 18090;
  private static final String PKCS12 = "PKCS12";
  private static final String OLD_CLIENT_REF = "opencop/shared/connect/source/old/client";
  private static final String OLD_TRUSTSTORE_REF = "opencop/shared/connect/source/old/truststore";
  private static final String NEW_CLIENT_REF = "opencop/shared/connect/source/new/client";
  private static final String INSTANCE_ID = "connect-1";
  private static final IntegrationConnectionSchemaReference NATIVE =
      NativeConnectionSchema.V1_REFERENCE;
  private static final IntegrationConnectionSchemaReference TAK = new com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference(java.util.UUID.fromString("714e8096-8832-4486-ad47-5422f86f918a"), 1);
  private static final ConnectionSpec TCP_SPEC =
      new ConnectionSpec(
          new TcpConfig("relay.example.test", 9090, null, null, null, null, new NoSecurity()));
  private static final ConnectionSpec UDP_SPEC =
      new ConnectionSpec(new UdpConfig("127.0.0.1", LISTEN_PORT, null));
  private static final PendingRestart PENDING =
      new PendingRestart(SOURCE_ID, INSTANCE_ID, 1, null, Instant.EPOCH);
  private static final RestartBaseline BASELINE =
      new RestartBaseline(INSTANCE_ID, null, Instant.EPOCH);

  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final ConnectionIntegrationSelection integrationSelection =
      mock(ConnectionIntegrationSelection.class);
  private final SelectedMapperLock selectedMapperLock = mock(SelectedMapperLock.class);
  private final ConnectionIntegrationSettingsGateway settingsGateway =
      mock(ConnectionIntegrationSettingsGateway.class);
  private final ConnectorRestart connectorRestart = mock(ConnectorRestart.class);
  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final ConnectionRecordGateway records = mock(ConnectionRecordGateway.class);
  private final ExtensionSecretEntries extensionSecretEntries = mock(ExtensionSecretEntries.class);
  private final AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler =
      mock(AmbiguousPersistenceOutcomeHandler.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final ConnectionSetupPersistence persistence =
      new ConnectionSetupPersistence(
          connections,
          dataSources,
          new ConnectionSetupRechecks(integrationSelection, selectedMapperLock, dataSources),
          settingsGateway,
          records,
          connectorRestart,
          connectionStore,
          extensionSecretEntries,
          new CompensatedTransaction(
              new TransactionTemplate(transactionManager), ambiguousOutcomeHandler));

  @Test
  void storesTheSetupOverTheLockedRowThenRestartsTheConnectionOnIt() {
    givenStored(TCP_SPEC, true);
    var setup = setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty());

    persistence.replaceAndRestart(SOURCE_ID, setup, USER_ID);

    var order =
        inOrder(
            connections,
            integrationSelection,
            selectedMapperLock,
            dataSources,
            settingsGateway,
            connectorRestart);
    order.verify(connectorRestart).baseline(SOURCE_ID, INSTANCE_ID);
    order.verify(connections).lockById(SOURCE_ID);
    order.verify(integrationSelection).requireSelectableForUpdate(NATIVE);
    order.verify(selectedMapperLock).hold(new ReadAccess(MAPPER_ID));
    order.verify(settingsGateway).delete(SOURCE_ID);
    order.verify(dataSources).updateSetup(SOURCE_ID, setup.stored(), USER_ID);
    order.verify(connectorRestart).request(SOURCE_ID, BASELINE, INSTANCE_ID);
    order.verify(connectorRestart).applyAndObserve(PENDING);
  }

  /** A failover while the setup was resolved moves the connection; it restarts where it went. */
  @Test
  void restartsTheConnectionOnTheInstanceItIsAssignedToOnceLocked() {
    givenStored(TCP_SPEC, true);
    when(dataSources.findById(SOURCE_ID))
        .thenReturn(Optional.of(stored(TCP_SPEC, true, "connect-2")));
    when(connectorRestart.request(eq(SOURCE_ID), any(), eq("connect-2"))).thenReturn(PENDING);

    persistence.replaceAndRestart(
        SOURCE_ID, setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty()), USER_ID);

    verify(connectorRestart).request(eq(SOURCE_ID), any(), eq("connect-2"));
  }

  @Test
  void storesADisabledConnectionsSetupWithoutRestartingIt() {
    givenStored(TCP_SPEC, false);
    var setup = setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty());

    persistence.replaceAndRestart(SOURCE_ID, setup, USER_ID);

    verify(dataSources).updateSetup(SOURCE_ID, setup.stored(), USER_ID);
    verifyNoRestart();
  }

  @Test
  void deletesTheSecretsOnlyTheReplacedSetupReadOnceTheConnectionRestarted() {
    givenStored(mtls(OLD_CLIENT_REF, OLD_TRUSTSTORE_REF), true);

    persistence.replaceAndRestart(
        SOURCE_ID,
        setup(mtls(NEW_CLIENT_REF, OLD_TRUSTSTORE_REF), SettingsDocument.empty(), Optional.empty()),
        USER_ID);

    var order = inOrder(connectorRestart, connectionStore);
    order.verify(connectorRestart).applyAndObserve(PENDING);
    order.verify(connectionStore).delete(List.of(OLD_CLIENT_REF));
  }

  /** Switching to plain leaves the connection no secrets, so every one it had is deleted. */
  @Test
  void deletesEverySecretWhenTheSetupDropsItsSecurity() {
    givenStored(mtls(OLD_CLIENT_REF, OLD_TRUSTSTORE_REF), true);

    persistence.replaceAndRestart(
        SOURCE_ID, setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty()), USER_ID);

    verify(connectionStore).delete(List.of(OLD_CLIENT_REF, OLD_TRUSTSTORE_REF));
  }

  @Test
  void deletesNothingWhenTheReplacedSetupHadNoSecrets() {
    givenStored(TCP_SPEC, true);

    persistence.replaceAndRestart(
        SOURCE_ID,
        setup(mtls(NEW_CLIENT_REF, null), SettingsDocument.empty(), Optional.empty()),
        USER_ID);

    verifyNoInteractions(connectionStore);
  }

  /** The new setup is stored and running, so secrets left behind do not fail the replacement. */
  @Test
  void completesEvenWhenTheReplacedSecretsCannotBeDeleted() {
    givenStored(mtls(OLD_CLIENT_REF, null), true);
    doThrow(new IllegalStateException("vault down")).when(connectionStore).delete(any());

    persistence.replaceAndRestart(
        SOURCE_ID, setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty()), USER_ID);

    verify(connectorRestart).applyAndObserve(PENDING);
  }

  @Test
  void replacesTheStoredSettingsWithTheSetupsOwn() {
    givenStored(TCP_SPEC, true);

    persistence.replaceAndRestart(SOURCE_ID, setup(TCP_SPEC, bravo(), Optional.empty()), USER_ID);

    var order = inOrder(settingsGateway, dataSources);
    order.verify(settingsGateway).delete(SOURCE_ID);
    order.verify(dataSources).updateSetup(eq(SOURCE_ID), any(), eq(USER_ID));
    order.verify(settingsGateway).save(eq(SOURCE_ID), eq(NATIVE), any());
  }

  /**
   * The stored settings name the integration they were validated against, so they are removed
   * before the connection is bound to another one, and the setup's own are stored for it after.
   */
  @Test
  void movesTheConnectionToAnotherIntegrationWithTheSetupsSettings() {
    givenStored(TCP_SPEC, true);
    var setup = setup(TAK, TCP_SPEC, bravo(), Optional.empty());

    persistence.replaceAndRestart(SOURCE_ID, setup, USER_ID);

    var order = inOrder(settingsGateway, dataSources);
    order.verify(settingsGateway).delete(SOURCE_ID);
    order.verify(dataSources).updateSetup(SOURCE_ID, setup.stored(), USER_ID);
    order.verify(settingsGateway).save(eq(SOURCE_ID), eq(TAK), any());
  }

  /**
   * The secret entries the left integration's extensions wrote are no longer reachable by any
   * extension, so they are deleted once the move commits.
   */
  @Test
  void deletesTheLeftIntegrationsExtensionSecretEntriesOnceTheMoveCommits() {
    givenStored(TCP_SPEC, true);
    var setup = setup(TAK, TCP_SPEC, bravo(), Optional.empty());

    persistence.replaceAndRestart(SOURCE_ID, setup, USER_ID);

    var order = inOrder(dataSources, transactionManager, extensionSecretEntries);
    order.verify(dataSources).updateSetup(SOURCE_ID, setup.stored(), USER_ID);
    order.verify(transactionManager).commit(any());
    order.verify(extensionSecretEntries).retire(SOURCE_ID, NATIVE.integrationId());
  }

  /**
   * Records are user data written for the connection's integration: moving the connection to
   * another one is refused before anything is stored, so the settings, setup and secrets stay.
   */
  @Test
  void refusesToMoveAConnectionWithRecordsToAnotherIntegrationBeforeStoringAnything() {
    givenStored(TCP_SPEC, true);
    when(records.existsFor(SOURCE_ID)).thenReturn(true);
    var cleanup = mock(Runnable.class);

    assertRejected(
        setup(TAK, TCP_SPEC, SettingsDocument.empty(), Optional.of(cleanup)),
        ConnectionExceptionCodes.CONNECTION_RECORDS_PRESENT);
    verify(settingsGateway, never()).delete(any());
    verify(dataSources, never()).updateSetup(any(), any(), any());
    verify(cleanup).run();
    verifyNoRestart();
    verifyNoInteractions(extensionSecretEntries);
  }

  @Test
  void keepsTheRecordsOfAConnectionUpgradedWithinItsIntegration() {
    givenStored(TCP_SPEC, true);
    when(records.existsFor(SOURCE_ID)).thenReturn(true);
    var nextVersion = new IntegrationConnectionSchemaReference(NATIVE.integrationId(), 2);

    persistence.replaceAndRestart(
        SOURCE_ID,
        setup(nextVersion, TCP_SPEC, SettingsDocument.empty(), Optional.empty()),
        USER_ID);

    verify(dataSources).updateSetup(eq(SOURCE_ID), any(), eq(USER_ID));
    verifyNoInteractions(extensionSecretEntries);
  }

  /** A connection whose integration can no longer be used can still be moved off it. */
  @Test
  void checksOnlyTheRequestedIntegrationSoAConnectionCanLeaveAnUnusableOne() {
    givenStored(TCP_SPEC, true);

    persistence.replaceAndRestart(
        SOURCE_ID, setup(TAK, TCP_SPEC, SettingsDocument.empty(), Optional.empty()), USER_ID);

    verify(integrationSelection).requireSelectableForUpdate(TAK);
    verify(integrationSelection, never()).requireSelectableForUpdate(NATIVE);
  }

  @Test
  void refusesARequestedIntegrationThatCanNoLongerBeUsedBeforeStoringAnything() {
    givenStored(TCP_SPEC, true);
    var cleanup = mock(Runnable.class);
    doThrow(new ConnectionManagementException(ConnectionExceptionCodes.INTEGRATION_INACTIVE))
        .when(integrationSelection)
        .requireSelectableForUpdate(TAK);

    assertRejected(
        setup(TAK, TCP_SPEC, SettingsDocument.empty(), Optional.of(cleanup)),
        ConnectionExceptionCodes.INTEGRATION_INACTIVE);
    verify(dataSources, never()).updateSetup(any(), any(), any());
    verifyNoInteractions(settingsGateway);
    verify(cleanup).run();
  }

  @Test
  void keepsTheListenPortTheConnectionAlreadyReserves() {
    givenStored(UDP_SPEC, true);
    when(dataSources.findActiveByListenPort(TransportKind.UDP, LISTEN_PORT))
        .thenReturn(Optional.of(SOURCE_ID));

    persistence.replaceAndRestart(
        SOURCE_ID, setup(UDP_SPEC, SettingsDocument.empty(), Optional.empty()), USER_ID);

    verify(dataSources).updateSetup(eq(SOURCE_ID), any(), eq(USER_ID));
  }

  @Test
  void refusesAListenPortAnotherSourceReservesAndDeletesTheNewMaterial() {
    givenStored(UDP_SPEC, true);
    var cleanup = mock(Runnable.class);
    when(dataSources.findActiveByListenPort(TransportKind.UDP, LISTEN_PORT))
        .thenReturn(Optional.of(OTHER_SOURCE_ID));

    assertRejected(
        setup(UDP_SPEC, SettingsDocument.empty(), Optional.of(cleanup)),
        ConnectionExceptionCodes.LISTEN_PORT_IN_USE);
    verify(dataSources, never()).updateSetup(any(), any(), any());
    verify(cleanup).run();
    verifyNoRestart();
  }

  @Test
  void reportsAListenPortTakenWhileStoringAsInUse() {
    givenStored(UDP_SPEC, true);
    doThrow(
            new DataSourceListenPortConflictException(
                LISTEN_PORT, new IllegalStateException("taken")))
        .when(dataSources)
        .updateSetup(eq(SOURCE_ID), any(), eq(USER_ID));

    assertRejected(
        setup(UDP_SPEC, SettingsDocument.empty(), Optional.empty()),
        ConnectionExceptionCodes.LISTEN_PORT_IN_USE);
  }

  @Test
  void refusesAConnectionArchivedWhileItsSetupWasResolved() {
    givenStored(mtls(OLD_CLIENT_REF, null), true);
    when(connections.lockById(SOURCE_ID))
        .thenReturn(metadata(TransportKind.TCP, true, Instant.now()));
    var cleanup = mock(Runnable.class);

    assertThatThrownBy(
            () ->
                persistence.replaceAndRestart(
                    SOURCE_ID,
                    setup(TCP_SPEC, SettingsDocument.empty(), Optional.of(cleanup)),
                    USER_ID))
        .isInstanceOf(GlobalOpenCopException.class);
    verify(cleanup).run();
    verify(dataSources, never()).updateSetup(any(), any(), any());
    verifyNoInteractions(connectionStore);
  }

  /** A commit that fails may still have stored the setup, so its material is kept. */
  @Test
  void keepsTheNewMaterialWhenTheCommitsOutcomeIsUnknown() {
    givenStored(mtls(OLD_CLIENT_REF, null), true);
    var cleanup = mock(Runnable.class);
    var failure = new TransactionSystemException("commit failed");
    doThrow(failure).when(transactionManager).commit(any());

    assertThatThrownBy(
            () ->
                persistence.replaceAndRestart(
                    SOURCE_ID,
                    setup(
                        mtls(NEW_CLIENT_REF, null), SettingsDocument.empty(), Optional.of(cleanup)),
                    USER_ID))
        .isSameAs(failure);
    verify(ambiguousOutcomeHandler)
        .preserveReplacementAfterFailure(
            SOURCE_ID, TransportKind.TCP, List.of(NEW_CLIENT_REF), failure);
    verify(ambiguousOutcomeHandler, never()).preserveAfterFailure(any(), any(), any(), any());
    verify(cleanup, never()).run();
    verify(connectorRestart, never()).applyAndObserve(any());
    verifyNoInteractions(connectionStore);
  }

  /** A setup that provisioned nothing commits as uncertainly, so its outcome is recorded too. */
  @Test
  void recordsAnUnknownCommitOutcomeForASetupThatProvisionedNothing() {
    givenStored(mtls(OLD_CLIENT_REF, null), true);
    var failure = new TransactionSystemException("commit failed");
    doThrow(failure).when(transactionManager).commit(any());

    assertThatThrownBy(
            () ->
                persistence.replaceAndRestart(
                    SOURCE_ID,
                    setup(TCP_SPEC, SettingsDocument.empty(), Optional.empty()),
                    USER_ID))
        .isSameAs(failure);
    verify(ambiguousOutcomeHandler)
        .preserveReplacementAfterFailure(SOURCE_ID, TransportKind.TCP, List.of(), failure);
    verifyNoInteractions(connectionStore);
  }

  /** The new material was provisioned before the baseline is read, so a failed read deletes it. */
  @Test
  void deletesTheNewMaterialWhenTheRestartBaselineCannotBeRead() {
    var cleanup = mock(Runnable.class);
    var failure = new IllegalStateException("database down");
    when(dataSources.findById(SOURCE_ID)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                persistence.replaceAndRestart(
                    SOURCE_ID,
                    setup(
                        mtls(NEW_CLIENT_REF, null), SettingsDocument.empty(), Optional.of(cleanup)),
                    USER_ID))
        .isSameAs(failure);
    verify(cleanup).run();
    verifyNoInteractions(transactionManager, connections, settingsGateway, connectionStore);
    verify(dataSources, never()).updateSetup(any(), any(), any());
    verifyNoRestart();
  }

  @Test
  void attachesAFailedDeletionOfTheNewMaterialToTheBaselineReadFailure() {
    var failure = new IllegalStateException("database down");
    var cleanupFailure = new IllegalStateException("vault down");
    when(dataSources.findById(SOURCE_ID)).thenThrow(failure);

    assertThatThrownBy(
            () ->
                persistence.replaceAndRestart(
                    SOURCE_ID,
                    setup(
                        mtls(NEW_CLIENT_REF, null),
                        SettingsDocument.empty(),
                        Optional.of(
                            () -> {
                              throw cleanupFailure;
                            })),
                    USER_ID))
        .isSameAs(failure)
        .hasSuppressedException(cleanupFailure);
  }

  private void assertRejected(ConnectionSetup setup, ConnectionExceptionCodes code) {
    assertThatThrownBy(() -> persistence.replaceAndRestart(SOURCE_ID, setup, USER_ID))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code.code()));
  }

  private void givenStored(ConnectionSpec spec, boolean enabled) {
    when(dataSources.findById(SOURCE_ID))
        .thenReturn(Optional.of(stored(spec, enabled, INSTANCE_ID)));
    when(connections.lockById(SOURCE_ID))
        .thenReturn(metadata(spec.transport().kind(), enabled, null));
    when(connectorRestart.baseline(SOURCE_ID, INSTANCE_ID)).thenReturn(BASELINE);
    if (enabled) {
      when(connectorRestart.request(SOURCE_ID, BASELINE, INSTANCE_ID)).thenReturn(PENDING);
    }
  }

  /** The baseline is read before anything is known, so only the restart itself is checked. */
  private void verifyNoRestart() {
    verify(connectorRestart, never()).request(any(), any(), any());
    verify(connectorRestart, never()).applyAndObserve(any());
  }

  private static SettingsDocument bravo() {
    return new SettingsDocument(Map.of("presence", Map.of("callsign", "Bravo")));
  }

  private static ConnectionSpec mtls(String clientRef, String truststoreRef) {
    return new ConnectionSpec(
        new TcpConfig(
            "relay.example.test",
            9090,
            null,
            null,
            null,
            null,
            new MtlsConfig(
                null,
                null,
                true,
                new KeystoreRef(truststoreRef, PKCS12),
                new KeystoreRef(clientRef, PKCS12))));
  }

  private static SocketDataSource stored(
      ConnectionSpec spec, boolean enabled, String assignedInstance) {
    return new SocketDataSource(
        SOURCE_ID,
        "Feed",
        "default",
        spec,
        MAPPER_ID,
        null,
        NATIVE,
        AccessMode.READ,
        enabled,
        assignedInstance,
        null,
        null,
        Instant.EPOCH,
        USER_ID,
        Instant.EPOCH,
        USER_ID,
        null,
        true);
  }

  private static ConnectionMetadata metadata(
      TransportKind kind, boolean enabled, Instant archivedAt) {
    return new ConnectionMetadata(
        SOURCE_ID, "Feed", MAPPER_ID, kind, AccessMode.READ, enabled, archivedAt, true, NATIVE);
  }

  private static ConnectionSetup setup(
      ConnectionSpec spec, SettingsDocument settings, Optional<Runnable> cleanup) {
    return setup(NATIVE, spec, settings, cleanup);
  }

  private static ConnectionSetup setup(
      IntegrationConnectionSchemaReference integration,
      ConnectionSpec spec,
      SettingsDocument settings,
      Optional<Runnable> cleanup) {
    return new ConnectionSetup(
        new ReadAccess(MAPPER_ID),
        integration,
        spec,
        settings,
        new SetupPrefill(EXTRACTOR_ID, Map.of()),
        cleanup);
  }
}
