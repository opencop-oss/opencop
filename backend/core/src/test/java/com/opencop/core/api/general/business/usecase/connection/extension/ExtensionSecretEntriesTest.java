// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ExtensionSecretEntriesTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final TestConnectionSchema V1 =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final TestConnectionSchema V2 =
      new TestConnectionSchema(V1.integrationId(), 2, "Test", "Test integration");
  private static final IntegrationConnectionSchemaReference OTHER =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final Instant ARCHIVED_AT = Instant.parse("2026-10-13T00:00:00Z");

  private final ExtensionSecretGateway extensionSecrets = mock(ExtensionSecretGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final DeleteHookTelemetry telemetry = mock(DeleteHookTelemetry.class);
  private final ExtensionSecretEntries entries =
      entries(new Extension(V1, "alpha"), new Extension(V1, "dropped"), new Extension(V2, "alpha"));

  @Test
  void aDeletedConnectionLosesTheEntriesOfEveryVersionsExtensions() {
    givenLocked(ARCHIVED_AT, V2.reference());

    entries.retire(SOURCE_ID, V1.integrationId());

    var order = inOrder(connections, extensionSecrets);
    order.verify(connections).lockById(SOURCE_ID);
    order.verify(extensionSecrets).delete(SOURCE_ID, V1.integrationId(), "alpha");
    order.verify(extensionSecrets).delete(SOURCE_ID, V1.integrationId(), "dropped");
    order.verifyNoMoreInteractions();
  }

  @Test
  void aConnectionThatLeftTheIntegrationLosesItsEntries() {
    givenLocked(null, OTHER);

    entries.retire(SOURCE_ID, V1.integrationId());

    verify(extensionSecrets).delete(SOURCE_ID, V1.integrationId(), "alpha");
    verify(extensionSecrets).delete(SOURCE_ID, V1.integrationId(), "dropped");
  }

  /** It left and came back since, and may have written an entry its extension reads again. */
  @Test
  void aConnectionBoundToTheIntegrationAgainKeepsItsEntries() {
    givenLocked(null, V1.reference());

    entries.retire(SOURCE_ID, V1.integrationId());

    verify(connections).lockById(SOURCE_ID);
    verifyNoInteractions(extensionSecrets, telemetry);
  }

  @Test
  void entriesThatCannotBeCheckedAreCountedAsNotDeleted() {
    when(connections.lockById(SOURCE_ID)).thenThrow(new IllegalStateException("database down"));

    entries.retire(SOURCE_ID, V1.integrationId());

    verifyNoInteractions(extensionSecrets);
    verify(telemetry).recordSecretEntryNotDeleted(V1.integrationId(), "alpha");
    verify(telemetry).recordSecretEntryNotDeleted(V1.integrationId(), "dropped");
  }

  @Test
  void anIntegrationDeclaringNoExtensionHereHasNothingToRetire() {
    entries.retire(SOURCE_ID, OTHER.integrationId());

    verifyNoInteractions(connections, extensionSecrets);
    verify(telemetry, never()).recordSecretEntryNotDeleted(any(), anyString());
  }

  private void givenLocked(Instant archivedAt, IntegrationConnectionSchemaReference integration) {
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "connection",
                null,
                TransportKind.TCP,
                AccessMode.READ,
                archivedAt == null,
                archivedAt,
                true,
                integration));
  }

  private ExtensionSecretEntries entries(Extension... declared) {
    List<IntegrationConnectionSchema> schemas = List.of(V1, V2);
    var registry =
        new ConnectionExtensionRegistry(
            schemas, List.of(declared), new ConnectionSettingsRegistry(schemas, List.of()));
    return new ExtensionSecretEntries(
        registry,
        extensionSecrets,
        new TransactionalConnectionLock(
            connections, new TransactionTemplate(mock(PlatformTransactionManager.class))),
        telemetry);
  }

  private record Extension(IntegrationConnectionSchema schema, String id)
      implements ConnectionExtensionCapability<NoConnectionSettings> {

    @Override
    public Class<NoConnectionSettings> settingsType() {
      return NoConnectionSettings.class;
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
  }
}
