// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionRequestBody;
import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionResult;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtensionCommand;
import com.opencop.core.api.general.ports.usecase.connection.extension.UnreadableExtensionBodyException;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.KeystoreRef;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionFailure;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionRequest;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InvokeConnectionExtensionUsecaseTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final IntegrationConnectionSchemaReference UNREGISTERED =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final String EXTENSION_ID = "data-sync";
  private static final String PLUGIN_CODE = "MISSION_NOT_FOUND";
  private static final String PLUGIN_MESSAGE = "Mission alpha does not exist";
  private static final String INTERNAL_DETAIL = "secret-host.internal:8443 refused";
  private static final UdpConfig UDP = new UdpConfig("remote.example", 4242, null);
  private static final String CLIENT_REF = "opencop/shared/connect/source/generation/client";
  private static final String OTHER_CONNECTIONS_REF =
      "opencop/shared/connect/other/generation/client";

  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionAuthorization authorization = mock(ConnectionAuthorization.class);
  private final ConnectionIntegrationSettingsGateway settingsGateway =
      mock(ConnectionIntegrationSettingsGateway.class);
  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final ExtensionSecretGateway extensionSecrets = mock(ExtensionSecretGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final FixtureExtension extension = new FixtureExtension();
  private final InvokeConnectionExtensionUsecase usecase = usecase(List.of(SCHEMA));
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(InvokeConnectionExtensionUsecase.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  private Level previousLevel;

  @BeforeEach
  void administerTheConnection() {
    when(authorization.isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false)).thenReturn(true);
  }

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logs.stop();
    logger.setLevel(previousLevel);
  }

  @Test
  void aViewerInvokesAReadOperationWithTheConnectionsContext() {
    givenConnection(SCHEMA.reference(), null);

    var result = usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha")));

    assertThat(result).isEqualTo(ExtensionResult.of("mission alpha"));
    var context = extension.lastContext.get();
    assertThat(context.sourceId()).isEqualTo(SOURCE_ID);
    assertThat(context.integration()).isEqualTo(SCHEMA.reference());
    assertThat(context.settings()).isSameAs(NoConnectionSettings.INSTANCE);
    assertThat(context.setup()).contains(new ConnectionSpec(UDP));
    assertThat(context.actingUserId()).isEqualTo(USER_ID);
    assertThat(extension.lastRequest.get().query()).containsEntry("limit", List.of("5"));
    verify(authorization).assertCanRead(SOURCE_ID, USER_ID, false);
    verify(authorization, never()).isDirectOwnerOrAdmin(any(), any(), any(Boolean.class));
    verify(authorization, never()).assertDirectOwnerOrAdmin(any(), any(), any(Boolean.class));
  }

  @Test
  void theExtensionReadsOnlyTheSecretsItsConnectionsSetupReferences() {
    givenConnection(SCHEMA.reference(), null, mtlsTcp());
    var stored = Map.of("b64", "material", "password", "secret");
    when(connectionStore.read(CLIENT_REF)).thenReturn(stored);

    usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha")));

    var secrets = extension.lastContext.get().secrets();
    assertThat(secrets.read(CLIENT_REF)).isEqualTo(stored);
    assertThatThrownBy(() -> secrets.read(OTHER_CONNECTIONS_REF))
        .isInstanceOf(IllegalArgumentException.class);
    verify(connectionStore, never()).read(OTHER_CONNECTIONS_REF);
  }

  @Test
  void theExtensionWritesOnlyItsOwnSecretEntryOnItsConnection() {
    givenConnection(SCHEMA.reference(), null);
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "connection",
                null,
                TransportKind.UDP,
                AccessMode.READ,
                true,
                null,
                true,
                SCHEMA.reference()));

    usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha")));
    extension.lastContext.get().secrets().entry().replace(Map.of("token", "t"));

    verify(extensionSecrets)
        .replace(SOURCE_ID, SCHEMA.integrationId(), EXTENSION_ID, Map.of("token", "t"));
  }

  @Test
  void aConnectionWithoutSecretsLetsTheExtensionReadNone() {
    givenConnection(SCHEMA.reference(), null);

    usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha")));

    var secrets = extension.lastContext.get().secrets();
    assertThatThrownBy(() -> secrets.read(CLIENT_REF)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aJsonOperationReturningNothingIsAnOpaqueServerError() {
    givenConnection(SCHEMA.reference(), null);
    extension.returnsNothing = true;

    assertCode(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        ConnectionExceptionCodes.EXTENSION_FAILED);
  }

  @Test
  void anOwnerInvokesAManageOperationWithItsJsonBody() {
    givenConnection(SCHEMA.reference(), null);
    var body = mock(ExtensionRequestBody.class);
    when(body.read(Subscribe.class)).thenReturn(new Subscribe("subscriber"));

    var result =
        usecase.invoke(command(ExtensionMethod.PUT, List.of("subscriptions", "alpha"), body));

    assertThat(result).isEqualTo(ExtensionResult.of(new Subscribed("alpha", "subscriber")));
    verify(authorization).isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);
  }

  @Test
  void anOperationReturningNoBodyAnswersWithoutOne() {
    givenConnection(SCHEMA.reference(), null);

    var result = usecase.invoke(command(ExtensionMethod.DELETE, List.of("subscriptions", "a")));

    assertThat(result).isEqualTo(ExtensionResult.none());
  }

  @Test
  void aMissingConnectionIsNotFound() {
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.empty());

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        HttpStatus.NOT_FOUND);
    assertNotInvoked();
  }

  @Test
  void anArchivedConnectionIsNotFound() {
    givenConnection(SCHEMA.reference(), Instant.parse("2026-10-01T00:00:00Z"));

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        HttpStatus.NOT_FOUND);
    verify(authorization, never()).assertCanRead(any(), any(), any(Boolean.class));
    assertNotInvoked();
  }

  @Test
  void aConnectionTheUserCannotSeeIsNotFoundBeforeItsExtensionsAreConsidered() {
    givenConnection(SCHEMA.reference(), null);
    doThrow(new GlobalOpenCopException("Data source not found", HttpStatus.NOT_FOUND))
        .when(authorization)
        .assertCanRead(SOURCE_ID, USER_ID, false);

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("nothing-declared"))),
        HttpStatus.NOT_FOUND);
    assertNotInvoked();
  }

  /**
   * Visibility comes before availability: a caller who cannot see the connection must not learn
   * that its integration does not run here.
   */
  @Test
  void aConnectionTheUserCannotSeeIsNotFoundEvenWhenItsIntegrationDoesNotRunHere() {
    givenConnection(UNREGISTERED, null);
    doThrow(new GlobalOpenCopException("Data source not found", HttpStatus.NOT_FOUND))
        .when(authorization)
        .assertCanRead(SOURCE_ID, USER_ID, false);

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        HttpStatus.NOT_FOUND);
    assertNotInvoked();
  }

  /** The access check comes before the body: a refused caller's body is never even read. */
  @Test
  void aManageCallerWhoIsRefusedHasTheirBodyLeftUnread() {
    givenConnection(SCHEMA.reference(), null);
    givenARefusedManager();
    var body = mock(ExtensionRequestBody.class);
    when(body.read(Subscribe.class))
        .thenThrow(new UnreadableExtensionBodyException("bad", new IllegalStateException()));

    assertThatThrownBy(
            () ->
                usecase.invoke(
                    command(ExtensionMethod.PUT, List.of("subscriptions", "alpha"), body)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(body);
    assertNotInvoked();
  }

  @Test
  void anIntegrationThisRuntimeDoesNotRegisterIsUnavailable() {
    givenConnection(UNREGISTERED, null);

    assertCode(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE);
    assertNotInvoked();
  }

  @Test
  void aConnectionWithoutAnIntegrationHasNoExtension() {
    givenConnection(null, null);

    assertCode(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        ConnectionExceptionCodes.CONNECTION_EXTENSION_NOT_FOUND);
  }

  @Test
  void anUndeclaredExtensionIsNotFound() {
    givenConnection(SCHEMA.reference(), null);
    var command =
        new InvokeConnectionExtensionCommand(
            SOURCE_ID,
            USER_ID,
            false,
            "other",
            ExtensionMethod.GET,
            List.of("missions", "alpha"),
            Map.of(),
            mock(ExtensionRequestBody.class));

    assertCode(
        () -> usecase.invoke(command), ConnectionExceptionCodes.CONNECTION_EXTENSION_NOT_FOUND);
    assertNotInvoked();
  }

  @Test
  void anUndeclaredOperationIsNotFound() {
    givenConnection(SCHEMA.reference(), null);

    assertCode(
        () -> usecase.invoke(command(ExtensionMethod.POST, List.of("missions", "alpha"))),
        ConnectionExceptionCodes.CONNECTION_EXTENSION_OPERATION_NOT_FOUND);
    assertNotInvoked();
  }

  @Test
  void aManageOperationRequiresADirectOwnerOrGlobalAdmin() {
    givenConnection(SCHEMA.reference(), null);
    givenARefusedManager();

    assertThatThrownBy(
            () ->
                usecase.invoke(command(ExtensionMethod.DELETE, List.of("subscriptions", "alpha"))))
        .isInstanceOf(AccessDeniedException.class);
    assertNotInvoked();
  }

  @Test
  void aManageCallLeavesAnAuditLineWithHowItEnded() {
    givenConnection(SCHEMA.reference(), null);
    var body = mock(ExtensionRequestBody.class);
    when(body.read(Subscribe.class)).thenReturn(new Subscribe("subscriber"));

    usecase.invoke(command(ExtensionMethod.PUT, List.of("subscriptions", "alpha"), body));

    assertThat(auditLines()).singleElement().asString().contains("userId=" + USER_ID, "status=200");
  }

  @Test
  void aRefusedManageCallIsAuditedAsForbidden() {
    givenConnection(SCHEMA.reference(), null);
    givenARefusedManager();

    assertThatThrownBy(
            () ->
                usecase.invoke(command(ExtensionMethod.DELETE, List.of("subscriptions", "alpha"))))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(auditLines()).singleElement().asString().contains("status=403");
  }

  /** A fault reading what the operation needs is no refusal, so the audit trail does not say so. */
  @Test
  void aManageCallFailingForAnotherReasonIsNotAuditedAsARefusal() {
    givenConnection(SCHEMA.reference(), null);
    var outage = new IllegalStateException("settings store unavailable");
    when(settingsGateway.find(SOURCE_ID)).thenThrow(outage);

    assertThatThrownBy(
            () ->
                usecase.invoke(command(ExtensionMethod.DELETE, List.of("subscriptions", "alpha"))))
        .isSameAs(outage);

    assertThat(auditLines()).singleElement().asString().contains("status=500");
    assertNotInvoked();
  }

  @Test
  void aReadCallLeavesNoAuditLine() {
    givenConnection(SCHEMA.reference(), null);

    usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha")));

    assertThat(auditLines()).isEmpty();
  }

  @Test
  void anUnreadableBodyIsABadRequest() {
    givenConnection(SCHEMA.reference(), null);
    var body = mock(ExtensionRequestBody.class);
    when(body.read(Subscribe.class))
        .thenThrow(new UnreadableExtensionBodyException("bad", new IllegalStateException()));

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.PUT, List.of("subscriptions", "a"), body)),
        HttpStatus.BAD_REQUEST);
    assertNotInvoked();
  }

  @Test
  void storedSettingsThatNoLongerResolveStopTheInvocation() {
    givenConnection(SCHEMA.reference(), null);
    when(settingsGateway.find(SOURCE_ID))
        .thenReturn(Optional.of(new SettingsOverrides(Map.of("audio", Map.of("volume", 3)))));

    assertCode(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        ConnectionExceptionCodes.CONNECTION_SETTINGS_UNRESOLVED);
    assertNotInvoked();
  }

  @Test
  void anExtensionFailureKeepsTheExtensionsCodeAndMessage() {
    givenConnection(SCHEMA.reference(), null);
    extension.failure =
        new ConnectionExtensionException(ExtensionFailure.NOT_FOUND, PLUGIN_CODE, PLUGIN_MESSAGE);

    assertThatThrownBy(
            () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> {
              assertThat(error.getCode()).isEqualTo(PLUGIN_CODE);
              assertThat(error.getMessage()).isEqualTo(PLUGIN_MESSAGE);
              assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            });
  }

  /**
   * The caller only sees the extension's safe message, so an upstream failure is logged with the
   * cause the extension attached, for operators to tell what failed.
   */
  @Test
  void anUpstreamFailureIsLoggedWithTheCauseTheExtensionAttached() {
    givenConnection(SCHEMA.reference(), null);
    extension.failure =
        new ConnectionExtensionException(
            ExtensionFailure.UPSTREAM_FAILURE,
            null,
            PLUGIN_MESSAGE,
            new IOException(INTERNAL_DETAIL));

    assertThatThrownBy(
            () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))))
        .isInstanceOf(GlobalOpenCopException.class)
        .hasMessageNotContaining(INTERNAL_DETAIL);

    var warning =
        assertThat(logs.list)
            .filteredOn(event -> event.getLevel() == Level.WARN)
            .singleElement()
            .actual();
    assertThat(warning.getFormattedMessage())
        .contains(
            "extension=" + EXTENSION_ID, "sourceId=" + SOURCE_ID, "EXTENSION_UPSTREAM_FAILURE");
    assertThat(warning.getThrowableProxy().getCause().getMessage()).isEqualTo(INTERNAL_DETAIL);
  }

  @Test
  void aFailureTheCallerCausedIsLoggedOnlyForDebugging() {
    givenConnection(SCHEMA.reference(), null);
    logger.setLevel(Level.DEBUG);
    extension.failure =
        new ConnectionExtensionException(ExtensionFailure.NOT_FOUND, PLUGIN_CODE, PLUGIN_MESSAGE);

    assertThatThrownBy(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))));

    assertThat(logs.list)
        .singleElement()
        .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
  }

  @Test
  void eachFailureFamilyAnswersWithItsStatusAndDefaultCode() {
    givenConnection(SCHEMA.reference(), null);
    var expectations =
        Map.of(
            ExtensionFailure.BAD_REQUEST, ConnectionExceptionCodes.EXTENSION_BAD_REQUEST,
            ExtensionFailure.NOT_FOUND, ConnectionExceptionCodes.EXTENSION_NOT_FOUND,
            ExtensionFailure.CONFLICT, ConnectionExceptionCodes.EXTENSION_CONFLICT,
            ExtensionFailure.UPSTREAM_FAILURE, ConnectionExceptionCodes.EXTENSION_UPSTREAM_FAILURE,
            ExtensionFailure.UPSTREAM_TIMEOUT, ConnectionExceptionCodes.EXTENSION_UPSTREAM_TIMEOUT);

    expectations.forEach(
        (family, expected) -> {
          extension.failure = new ConnectionExtensionException(family, PLUGIN_MESSAGE);
          assertCode(
              () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
              expected);
        });
  }

  @Test
  void aRefusalFromTheHostsOwnGatewaysKeepsItsStatus() {
    givenConnection(SCHEMA.reference(), null);
    extension.unexpected = new GlobalOpenCopException("Data source archived", HttpStatus.CONFLICT);

    assertStatus(
        () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))),
        HttpStatus.CONFLICT);
  }

  @Test
  void anUnexpectedExtensionErrorIsAnOpaqueServerError() {
    givenConnection(SCHEMA.reference(), null);
    extension.unexpected = new IllegalStateException(INTERNAL_DETAIL);

    assertThatThrownBy(
            () -> usecase.invoke(command(ExtensionMethod.GET, List.of("missions", "alpha"))))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> {
              assertThat(error.getCode())
                  .isEqualTo(ConnectionExceptionCodes.EXTENSION_FAILED.code());
              assertThat(error.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(error.getMessage()).doesNotContain(INTERNAL_DETAIL);
            });
  }

  private List<String> auditLines() {
    return logs.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(line -> line.startsWith("Connection extension operation invoked"))
        .toList();
  }

  private InvokeConnectionExtensionUsecase usecase(List<IntegrationConnectionSchema> schemas) {
    var settingsRegistry = new ConnectionSettingsRegistry(schemas, List.of());
    when(settingsGateway.find(any())).thenReturn(Optional.empty());
    return new InvokeConnectionExtensionUsecase(
        new ExtensionConnections(dataSources, authorization, schemas),
        authorization,
        new ConnectionExtensionRegistry(schemas, List.of(extension), settingsRegistry),
        new ExtensionContexts(
            settingsRegistry,
            settingsGateway,
            connectionStore,
            mock(ConnectionRecordGateway.class),
            new TransactionalConnectionLock(
                connections, new TransactionTemplate(mock(PlatformTransactionManager.class))),
            new RecordLimits(1_000, 10),
            extensionSecrets));
  }

  /** The caller does not administer the connection, and the authorization refuses them. */
  private void givenARefusedManager() {
    when(authorization.isDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false)).thenReturn(false);
    doThrow(new AccessDeniedException("denied"))
        .when(authorization)
        .assertDirectOwnerOrAdmin(SOURCE_ID, USER_ID, false);
  }

  private void givenConnection(IntegrationConnectionSchemaReference integration, Instant archived) {
    givenConnection(integration, archived, UDP);
  }

  private void givenConnection(
      IntegrationConnectionSchemaReference integration,
      Instant archived,
      TransportConfig transport) {
    DataSource connection =
        new SocketDataSource(
            SOURCE_ID,
            "connection",
            "tenant",
            new ConnectionSpec(transport),
            null,
            null,
            integration,
            AccessMode.READ,
            true,
            null,
            archived,
            archived == null ? null : USER_ID,
            null,
            null,
            null,
            null,
            null,
            true);
    when(dataSources.findById(SOURCE_ID)).thenReturn(Optional.of(connection));
  }

  private static TransportConfig mtlsTcp() {
    return new TcpConfig(
        "remote.example",
        8089,
        null,
        null,
        null,
        null,
        new MtlsConfig(null, null, true, null, new KeystoreRef(CLIENT_REF, "PKCS12")));
  }

  private static InvokeConnectionExtensionCommand command(
      ExtensionMethod method, List<String> path) {
    return command(method, path, mock(ExtensionRequestBody.class));
  }

  private static InvokeConnectionExtensionCommand command(
      ExtensionMethod method, List<String> path, ExtensionRequestBody body) {
    return new InvokeConnectionExtensionCommand(
        SOURCE_ID, USER_ID, false, EXTENSION_ID, method, path, Map.of("limit", List.of("5")), body);
  }

  private void assertNotInvoked() {
    assertThat(extension.lastContext.get()).isNull();
  }

  private static void assertStatus(Runnable call, HttpStatus status) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> assertThat(error.getHttpStatus()).isEqualTo(status));
  }

  private static void assertCode(Runnable call, ConnectionExceptionCodes code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error -> {
              assertThat(error.getCode()).isEqualTo(code.code());
              assertThat(error.getHttpStatus()).isEqualTo(code.httpStatus());
            });
  }

  record Subscribe(String role) {}

  record Subscribed(String mission, String role) {}

  /** A test-only extension recording what it was invoked with. */
  private static final class FixtureExtension
      implements ConnectionExtensionCapability<NoConnectionSettings> {

    private final AtomicReference<ConnectionExtensionContext<NoConnectionSettings>> lastContext =
        new AtomicReference<>();
    private final AtomicReference<ExtensionRequest<?>> lastRequest = new AtomicReference<>();
    private ConnectionExtensionException failure;
    private RuntimeException unexpected;
    private boolean returnsNothing;

    @Override
    public IntegrationConnectionSchema schema() {
      return SCHEMA;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {
      return NoConnectionSettings.class;
    }

    @Override
    public String id() {
      return EXTENSION_ID;
    }

    @Override
    public ExtensionDescriptor descriptor() {
      return new ExtensionDescriptor("Data sync", "Synchronises remote data");
    }

    @Override
    public List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations() {
      return List.of(
          ExtensionOperation.<NoConnectionSettings, Void, String>of(
              ExtensionMethod.GET,
              "/missions/{name}",
              AccessLevel.READ,
              ExtensionBody.none(),
              ExtensionBody.json(String.class),
              (context, request) -> {
                record(context, request);
                return returnsNothing ? null : "mission " + request.pathVariable("name");
              }),
          ExtensionOperation.<NoConnectionSettings, Subscribe, Subscribed>of(
              ExtensionMethod.PUT,
              "/subscriptions/{name}",
              AccessLevel.MANAGE,
              ExtensionBody.json(Subscribe.class),
              ExtensionBody.json(Subscribed.class),
              (context, request) -> {
                record(context, request);
                return new Subscribed(request.pathVariable("name"), request.body().role());
              }),
          ExtensionOperation.<NoConnectionSettings, Void, Void>of(
              ExtensionMethod.DELETE,
              "/subscriptions/{name}",
              AccessLevel.MANAGE,
              ExtensionBody.none(),
              ExtensionBody.none(),
              (context, request) -> {
                record(context, request);
                return null;
              }));
    }

    private void record(
        ConnectionExtensionContext<NoConnectionSettings> context, ExtensionRequest<?> request)
        throws ConnectionExtensionException {
      lastContext.set(context);
      lastRequest.set(request);
      if (failure != null) {
        throw failure;
      }
      if (unexpected != null) {
        throw unexpected;
      }
    }
  }
}
