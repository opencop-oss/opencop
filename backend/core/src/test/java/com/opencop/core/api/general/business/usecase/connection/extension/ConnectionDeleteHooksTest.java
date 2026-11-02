// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.connection.extension.ExecutorDeleteHookRunner;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExpectedRevision;
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
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionException;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionFailure;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ConnectionDeleteHooksTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final TestConnectionSchema NEXT_SCHEMA =
      new TestConnectionSchema(SCHEMA.integrationId(), 2, "Test", "Test integration");
  private static final IntegrationConnectionSchemaReference UNREGISTERED =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final String CLIENT_REF = "opencop/shared/connect/source/generation/client";
  private static final Map<String, String> CLIENT_MATERIAL = Map.of("b64", "material");
  private static final Duration TIMEOUT = Duration.ofMillis(200);

  private final DataSourceRepository dataSources = mock(DataSourceRepository.class);
  private final ConnectionStore connectionStore = mock(ConnectionStore.class);
  private final ConnectionIntegrationSettingsGateway settingsGateway =
      mock(ConnectionIntegrationSettingsGateway.class);
  private final DeleteHookTelemetry telemetry = mock(DeleteHookTelemetry.class);
  private final ExtensionSecretGateway extensionSecrets = mock(ExtensionSecretGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final List<String> calls = new ArrayList<>();
  private final AtomicReference<ConnectionExtensionContext<NoConnectionSettings>> seen =
      new AtomicReference<>();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  @BeforeEach
  void storeNoSettingsOverrides() {
    when(settingsGateway.find(SOURCE_ID)).thenReturn(Optional.empty());
  }

  /** The hooks run once the deletion is final, so the locked connection reads as archived. */
  @BeforeEach
  void archiveTheLockedConnection() {
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "connection",
                null,
                TransportKind.TCP,
                AccessMode.READ,
                false,
                Instant.parse("2026-10-13T00:00:00Z"),
                true,
                SCHEMA.reference()));
  }

  @AfterEach
  void stopExecutor() {
    executor.close();
  }

  @Test
  void theHookSeesTheSecretsReadBeforeTheDeletionRemovedThem() throws Exception {
    givenConnection(SCHEMA.reference());
    when(connectionStore.read(CLIENT_REF)).thenReturn(CLIENT_MATERIAL);
    var hooks = hooks(new Hook("sync", HookBehaviour.RECORD));

    var afterDeletion = hooks.prepare(SOURCE_ID, USER_ID);
    when(connectionStore.read(CLIENT_REF)).thenReturn(null);
    afterDeletion.run();

    var context = seen.get();
    assertThat(context.secrets().read(CLIENT_REF)).isEqualTo(CLIENT_MATERIAL);
    assertThat(context.settings()).isSameAs(NoConnectionSettings.INSTANCE);
    assertThat(context.actingUserId()).isEqualTo(USER_ID);
    assertThatThrownBy(() -> context.records().put("c", "k", Map.of(), ExpectedRevision.any()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void eachExtensionsSecretEntryIsDeletedOnceItsHookRan() {
    givenConnection(SCHEMA.reference());
    var hooks =
        hooks(new Hook("alpha", HookBehaviour.RECORD), new Hook("bravo", HookBehaviour.FAIL));

    hooks.prepare(SOURCE_ID, USER_ID).run();

    var order = inOrder(telemetry, extensionSecrets);
    order.verify(telemetry).recordFailure(SCHEMA.integrationId(), "bravo");
    order.verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "alpha");
    order.verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "bravo");
  }

  @Test
  void theHookReadsItsOwnSecretEntryButCannotWriteIt() {
    givenConnection(SCHEMA.reference());
    when(extensionSecrets.read(SOURCE_ID, SCHEMA.integrationId(), "sync"))
        .thenReturn(Map.of("token", "t"));

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    var entry = seen.get().secrets().entry();
    assertThat(entry.read()).containsEntry("token", "t");
    assertThatThrownBy(() -> entry.replace(Map.of("token", "u")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void secretEntriesAreDeletedEvenWhenTheHooksCannotRun() {
    givenConnection(SCHEMA.reference());
    when(settingsGateway.find(SOURCE_ID))
        .thenReturn(Optional.of(new SettingsOverrides(Map.of("audio", Map.of("volume", 3)))));

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).isEmpty();
    verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "sync");
    verify(telemetry).recordNotRun(SCHEMA.integrationId(), "sync");
  }

  /** A deletion that fails after the hooks were prepared leaves nothing behind to count. */
  @Test
  void hooksThatCannotRunAreCountedOnlyOnceTheDeletionIsFinal() {
    givenConnection(SCHEMA.reference());
    when(settingsGateway.find(SOURCE_ID))
        .thenReturn(Optional.of(new SettingsOverrides(Map.of("audio", Map.of("volume", 3)))));

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID);

    verifyNoInteractions(telemetry);
  }

  @Test
  void hooksStoppedByAnInterruptionAreCountedAsNotRun() {
    givenConnection(SCHEMA.reference());
    var afterDeletion =
        hooks(new Hook("alpha", HookBehaviour.RECORD), new Hook("bravo", HookBehaviour.RECORD))
            .prepare(SOURCE_ID, USER_ID);

    Thread.currentThread().interrupt();
    try {
      afterDeletion.run();
    } finally {
      Thread.interrupted();
    }

    assertThat(calls).isEmpty();
    verify(telemetry).recordNotRun(SCHEMA.integrationId(), "alpha");
    verify(telemetry).recordNotRun(SCHEMA.integrationId(), "bravo");
  }

  @Test
  void aSecretEntryThatCannotBeDeletedIsCounted() {
    givenConnection(SCHEMA.reference());
    doThrow(new IllegalStateException("store unavailable"))
        .when(extensionSecrets)
        .delete(SOURCE_ID, SCHEMA.integrationId(), "sync");

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    verify(telemetry).recordSecretEntryNotDeleted(SCHEMA.integrationId(), "sync");
  }

  @Test
  void nothingRunsUntilTheDeletionIsFinal() {
    givenConnection(SCHEMA.reference());

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID);

    assertThat(calls).isEmpty();
  }

  @Test
  void aFailingHookIsCountedAndTheNextOneStillRuns() {
    givenConnection(SCHEMA.reference());
    var hooks =
        hooks(new Hook("alpha", HookBehaviour.FAIL), new Hook("bravo", HookBehaviour.RECORD));

    hooks.prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).containsExactly("alpha", "bravo");
    verify(telemetry).recordFailure(SCHEMA.integrationId(), "alpha");
    verify(telemetry, never()).recordFailure(SCHEMA.integrationId(), "bravo");
  }

  @Test
  void aHookRunningPastItsLimitIsAbandonedAndCounted() {
    givenConnection(SCHEMA.reference());
    var hooks =
        hooks(new Hook("alpha", HookBehaviour.HANG), new Hook("bravo", HookBehaviour.RECORD));

    var started = System.nanoTime();
    hooks.prepare(SOURCE_ID, USER_ID).run();

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    verify(telemetry).recordTimeout(SCHEMA.integrationId(), "alpha");
    assertThat(calls).contains("bravo");
  }

  /** An entry belongs to the integration, so a version dropping an extension leaves its entry. */
  @Test
  void theEntryOfAnExtensionANewerVersionDroppedIsDeletedToo() {
    givenConnection(NEXT_SCHEMA.reference());

    hooks(
            List.of(SCHEMA, NEXT_SCHEMA),
            new Hook(SCHEMA, "dropped", HookBehaviour.RECORD),
            new Hook(NEXT_SCHEMA, "kept", HookBehaviour.RECORD))
        .prepare(SOURCE_ID, USER_ID)
        .run();

    assertThat(calls).containsExactly("kept");
    verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "dropped");
    verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "kept");
  }

  @Test
  void entriesAreDeletedForAVersionDeclaringNoExtension() {
    givenConnection(NEXT_SCHEMA.reference());

    hooks(List.of(SCHEMA, NEXT_SCHEMA), new Hook(SCHEMA, "dropped", HookBehaviour.RECORD))
        .prepare(SOURCE_ID, USER_ID)
        .run();

    assertThat(calls).isEmpty();
    verify(extensionSecrets).delete(SOURCE_ID, SCHEMA.integrationId(), "dropped");
  }

  @Test
  void anIntegrationThisRuntimeDoesNotRunHasNoHooks() {
    givenConnection(UNREGISTERED);

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).isEmpty();
    verifyNoInteractions(connectionStore);
  }

  @Test
  void settingsThatNoLongerResolveSkipTheHooksWithoutFailingTheDeletion() {
    givenConnection(SCHEMA.reference());
    when(settingsGateway.find(SOURCE_ID))
        .thenReturn(Optional.of(new SettingsOverrides(Map.of("audio", Map.of("volume", 3)))));

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).isEmpty();
  }

  @Test
  void anUnknownConnectionHasNoHooks() {
    when(dataSources.findById(any())).thenReturn(Optional.empty());

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).isEmpty();
  }

  @Test
  void anAlreadyArchivedConnectionHasNoHooks() {
    givenConnection(SCHEMA.reference(), Instant.parse("2026-10-01T00:00:00Z"));

    hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID).run();

    assertThat(calls).isEmpty();
  }

  @Test
  void hooksThatCannotBeStartedNeverFailTheDeletion() {
    givenConnection(SCHEMA.reference());
    var afterDeletion = hooks(new Hook("sync", HookBehaviour.RECORD)).prepare(SOURCE_ID, USER_ID);
    executor.shutdownNow();

    assertThatCode(afterDeletion::run).doesNotThrowAnyException();
    assertThat(calls).isEmpty();
    verify(telemetry).recordFailure(SCHEMA.integrationId(), "sync");
  }

  private ConnectionDeleteHooks hooks(Hook... extensions) {
    return hooks(List.of(SCHEMA), extensions);
  }

  private ConnectionDeleteHooks hooks(
      List<IntegrationConnectionSchema> schemas, Hook... extensions) {
    var settingsRegistry = new ConnectionSettingsRegistry(schemas, List.of());
    var contexts =
        new ExtensionContexts(
            settingsRegistry,
            settingsGateway,
            connectionStore,
            mock(ConnectionRecordGateway.class),
            new TransactionalConnectionLock(
                mock(ConnectionMetadataGateway.class),
                new TransactionTemplate(mock(PlatformTransactionManager.class))),
            new RecordLimits(1_000, 10),
            extensionSecrets);
    var registry = new ConnectionExtensionRegistry(schemas, List.of(extensions), settingsRegistry);
    return new ConnectionDeleteHooks(
        new ExtensionConnections(dataSources, mock(ConnectionAuthorization.class), schemas),
        registry,
        contexts,
        new ExtensionSecretEntries(
            registry,
            extensionSecrets,
            new TransactionalConnectionLock(
                connections, new TransactionTemplate(mock(PlatformTransactionManager.class))),
            telemetry),
        telemetry,
        new ExecutorDeleteHookRunner(executor),
        TIMEOUT);
  }

  private void givenConnection(IntegrationConnectionSchemaReference integration) {
    givenConnection(integration, null);
  }

  private void givenConnection(IntegrationConnectionSchemaReference integration, Instant archived) {
    var spec =
        new ConnectionSpec(
            new TcpConfig(
                "remote.example",
                8089,
                null,
                null,
                null,
                null,
                new MtlsConfig(null, null, true, null, new KeystoreRef(CLIENT_REF, "PKCS12"))));
    when(dataSources.findById(SOURCE_ID))
        .thenReturn(
            Optional.of(
                new SocketDataSource(
                    SOURCE_ID,
                    "connection",
                    "tenant",
                    spec,
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
                    true)));
  }

  private enum HookBehaviour {
    RECORD,
    FAIL,
    HANG
  }

  /** A test extension whose delete hook records its call, fails, or never returns. */
  private final class Hook implements ConnectionExtensionCapability<NoConnectionSettings> {

    private final IntegrationConnectionSchema schema;
    private final String id;
    private final HookBehaviour behaviour;

    private Hook(String id, HookBehaviour behaviour) {
      this(SCHEMA, id, behaviour);
    }

    private Hook(IntegrationConnectionSchema schema, String id, HookBehaviour behaviour) {
      this.schema = schema;
      this.id = id;
      this.behaviour = behaviour;
    }

    @Override
    public IntegrationConnectionSchema schema() {
      return schema;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {
      return NoConnectionSettings.class;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public ExtensionDescriptor descriptor() {
      return new ExtensionDescriptor(id, "Test extension");
    }

    @Override
    public List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations() {
      return List.of(
          ExtensionOperation.<NoConnectionSettings, Void, Void>of(
              ExtensionMethod.GET,
              "/",
              AccessLevel.READ,
              ExtensionBody.none(),
              ExtensionBody.none(),
              (context, request) -> null));
    }

    @Override
    public void afterConnectionDeleted(ConnectionExtensionContext<NoConnectionSettings> context)
        throws ConnectionExtensionException {
      synchronized (calls) {
        calls.add(id);
      }
      seen.set(context);
      switch (behaviour) {
        case RECORD -> {
          // Recorded above.
        }
        case FAIL ->
            throw new ConnectionExtensionException(
                ExtensionFailure.UPSTREAM_FAILURE, "The remote system refused");
        case HANG -> hang();
      }
    }

    private void hang() {
      try {
        Thread.sleep(Duration.ofSeconds(30));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
