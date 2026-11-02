// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.component.support.TestActors;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditChange;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.persistence.ConnectionSetupPersistence;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class ReplaceConnectionSetupUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
  private static final UUID USER_ID = UUID.fromString("3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f");
  private static final Caller ACTOR = TestActors.of(USER_ID);
  private static final UUID MAPPER_ID = UUID.fromString("4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f80");
  private static final UUID EXTRACTOR_ID = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-4c5d6e7f8091");
  private static final IntegrationConnectionSchemaReference NATIVE =
      NativeConnectionSchema.V1_REFERENCE;
  private static final ConnectionSpec TCP_SPEC =
      new ConnectionSpec(
          new TcpConfig("relay.example.test", 9090, null, null, null, null, new NoSecurity()));

  private final ConnectionAuthorization authorization = mock(ConnectionAuthorization.class);
  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionSetupResolution setupResolution = mock(ConnectionSetupResolution.class);
  private final ConnectionSetupPersistence setupPersistence =
      mock(ConnectionSetupPersistence.class);
  private final RetrieveConnection retrieveConnection = mock(RetrieveConnection.class);
  private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
  private final ReplaceConnectionSetupUsecase usecase =
      new ReplaceConnectionSetupUsecase(
          authorization,
          dataSources,
          setupResolution,
          setupPersistence,
          retrieveConnection,
          auditRecorder,
          new DataSourceAuditProjection());

  @Test
  void storesTheResolvedSetupThenAnswersTheConnectionAsItNowIs() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(null)));
    var resolved =
        new ConnectionSetup(
            new ReadAccess(MAPPER_ID),
            NATIVE,
            TCP_SPEC,
            SettingsDocument.empty(),
            new SetupPrefill(EXTRACTOR_ID, Map.of()),
            Optional.empty());
    when(setupResolution.resolve(any(ConnectionCreation.Tcp.class), eq(SOURCE_ID)))
        .thenReturn(resolved);
    var answered = mock(ConnectionInfo.class);
    when(retrieveConnection.get(SOURCE_ID, USER_ID)).thenReturn(answered);

    var result = usecase.replace(SOURCE_ID, tcp(), ACTOR);

    assertThat(result).isSameAs(answered);
    var order = inOrder(authorization, setupResolution, setupPersistence, retrieveConnection);
    order.verify(authorization).assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);
    order.verify(setupResolution).resolve(any(ConnectionCreation.Tcp.class), eq(SOURCE_ID));
    order
        .verify(setupPersistence)
        .replaceAndRestart(eq(SOURCE_ID), eq(resolved), eq(USER_ID), any());
    order.verify(retrieveConnection).get(SOURCE_ID, USER_ID);
  }

  @Test
  void refusesSomeoneWhoMayNotChangeTheConnectionBeforeReadingIt() {
    doThrow(new AccessDeniedException("denied"))
        .when(authorization)
        .assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);

    assertThatThrownBy(() -> usecase.replace(SOURCE_ID, tcp(), ACTOR))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(dataSources, setupResolution, setupPersistence);
  }

  @Test
  void refusesAConnectionThatDoesNotExist() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> usecase.replace(SOURCE_ID, tcp(), ACTOR))
        .isInstanceOf(GlobalOpenCopException.class);
    verifyNoInteractions(setupResolution, setupPersistence);
  }

  /** Nothing is provisioned for an archived connection, which keeps the setup it had. */
  @Test
  void refusesAnArchivedConnectionBeforeResolvingTheSetup() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(Instant.EPOCH)));

    assertThatThrownBy(() -> usecase.replace(SOURCE_ID, tcp(), ACTOR))
        .isInstanceOf(GlobalOpenCopException.class);
    verifyNoInteractions(setupResolution, setupPersistence);
  }

  @Test
  void refusesAKafkaConnection() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(kafka()));

    assertRejected(tcp(), ConnectionExceptionCodes.CONNECTION_SETUP_NOT_SUPPORTED);
  }

  @Test
  void refusesASetupOfAnotherTransportKind() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(null)));

    assertRejected(
        new ConnectionCreation.Udp(details()),
        ConnectionExceptionCodes.CONNECTION_KIND_CHANGE_UNSUPPORTED);
  }

  /** A setup rejected as a creation would be provisions and stores nothing. */
  @Test
  void storesNothingForASetupItsResolutionRejects() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(null)));
    when(setupResolution.resolve(any(ConnectionCreation.Tcp.class), eq(SOURCE_ID)))
        .thenThrow(new ConnectionManagementException(ConnectionExceptionCodes.PAIR_NOT_ALLOWED));

    assertRejected(tcp(), ConnectionExceptionCodes.PAIR_NOT_ALLOWED);
    verifyNoInteractions(setupPersistence);
  }

  /**
   * The setup's own fields come from a plugin descriptor and may be secrets or files, so the row
   * says which fields moved and never what they moved to.
   */
  @Test
  void recordsTheReplacedSetupByFieldNameWithoutAnyOfItsValues() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(null)));
    when(setupResolution.resolve(any(ConnectionCreation.Tcp.class), eq(SOURCE_ID)))
        .thenReturn(resolved());

    var locked = mock(ConnectionMetadata.class);
    when(locked.name()).thenReturn("Feed");
    doAnswer(
            invocation -> {
              invocation.<Consumer<ConnectionMetadata>>getArgument(3).accept(locked);
              return null;
            })
        .when(setupPersistence)
        .replaceAndRestart(eq(SOURCE_ID), any(), eq(USER_ID), any());

    usecase.replace(SOURCE_ID, tcpWithInput(), ACTOR);

    var recorded = ArgumentCaptor.forClass(AuditEvent.class);
    verify(auditRecorder).record(recorded.capture());
    var event = recorded.getValue();
    assertThat(event.entityType()).isEqualTo(EntityType.DATA_SOURCE);
    assertThat(event.entityId()).isEqualTo(SOURCE_ID);
    assertThat(event.entityName()).isEqualTo("Feed");
    assertThat(event.operation()).isEqualTo(Operation.SETUP_REPLACED);
    assertThat(event.actor()).isEqualTo(ACTOR);
    // No `changed` entry and nothing but the three name-only details: no supplied value can have
    // reached the row, neither the host nor the connection name.
    assertThat(event.change().changed()).isEmpty();
    assertThat(event.change().details())
        .containsOnlyKeys("fieldsSupplied", "settingsSupplied", "valuesOmitted")
        .containsEntry("valuesOmitted", true)
        .containsEntry("settingsSupplied", new AuditChange.NameList(List.of("presence")))
        .hasEntrySatisfying(
            "fieldsSupplied",
            supplied ->
                assertThat(((AuditChange.NameList) supplied).names())
                    .contains(
                        "access",
                        "integration",
                        "extractorId",
                        "mapperId",
                        "host",
                        "clientCertificate"));
  }

  /** A rejected replacement leaves the stored setup alone, so it must leave no row either. */
  @Test
  void recordsNothingForASetupItsResolutionRejects() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(storedTcp(null)));
    when(setupResolution.resolve(any(ConnectionCreation.Tcp.class), eq(SOURCE_ID)))
        .thenThrow(new ConnectionManagementException(ConnectionExceptionCodes.PAIR_NOT_ALLOWED));

    assertRejected(tcpWithInput(), ConnectionExceptionCodes.PAIR_NOT_ALLOWED);
    verifyNoInteractions(auditRecorder);
  }

  private void assertRejected(ConnectionCreation setup, ConnectionExceptionCodes code) {
    assertThatThrownBy(() -> usecase.replace(SOURCE_ID, setup, ACTOR))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code.code()));
    verifyNoInteractions(setupPersistence);
  }

  private static SocketDataSource storedTcp(Instant archivedAt) {
    return new SocketDataSource(
        SOURCE_ID,
        "Feed",
        "default",
        TCP_SPEC,
        MAPPER_ID,
        null,
        NATIVE,
        AccessMode.READ,
        true,
        "connect-1",
        archivedAt,
        null,
        Instant.EPOCH,
        USER_ID,
        Instant.EPOCH,
        USER_ID,
        null,
        true);
  }

  private static KafkaDataSource kafka() {
    return new KafkaDataSource(
        SOURCE_ID,
        "Topic",
        "default",
        "source." + SOURCE_ID,
        "producer",
        MAPPER_ID,
        NATIVE,
        AccessMode.READ,
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

  private static ConnectionCreation.Tcp tcp() {
    return new ConnectionCreation.Tcp(details(), null, null);
  }

  private static ConnectionSetup resolved() {
    return new ConnectionSetup(
        new ReadAccess(MAPPER_ID),
        NATIVE,
        TCP_SPEC,
        SettingsDocument.empty(),
        new SetupPrefill(EXTRACTOR_ID, Map.of()),
        Optional.empty());
  }

  /** A setup carrying a text field, an uploaded file and a settings override, all sensitive. */
  private static ConnectionCreation.Tcp tcpWithInput() {
    var input = mock(ConnectionInput.class);
    when(input.values())
        .thenReturn(List.of(new ConnectionInputValue("host", "relay.example.test")));
    when(input.resources()).thenReturn(List.of(resource("clientCertificate")));
    return new ConnectionCreation.Tcp(
        new ConnectionCreationDetails(
            null,
            "R",
            NATIVE,
            EXTRACTOR_ID,
            input,
            MAPPER_ID,
            ACTOR,
            Map.of("presence", Map.of("callsign", "Alpha"))),
        null,
        null);
  }

  private static ConnectionInputResource resource(String role) {
    return new ConnectionInputResource(
        role, role + ".pem", "application/x-pem-file", 1, () -> InputStream.nullInputStream());
  }

  private static ConnectionCreationDetails details() {
    return new ConnectionCreationDetails(
        null, "R", NATIVE, EXTRACTOR_ID, mock(ConnectionInput.class), MAPPER_ID, ACTOR, null);
  }
}
