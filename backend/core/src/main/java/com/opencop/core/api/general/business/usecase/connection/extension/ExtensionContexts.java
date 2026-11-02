// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.model.connection.extension.RecordLimits;
import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Builds what the host hands an extension about a connection, confined to that connection and the
 * extension: its settings, its setup, the secrets that setup references, and its records.
 */
public class ExtensionContexts {

  private final ConnectionSettingsRegistry settingsRegistry;
  private final ConnectionIntegrationSettingsGateway settingsGateway;
  private final ConnectionStore connectionStore;
  private final ConnectionRecordGateway records;
  private final ConnectionLock lock;
  private final RecordLimits recordLimits;
  private final ExtensionSecretGateway extensionSecrets;

  public ExtensionContexts(
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionIntegrationSettingsGateway settingsGateway,
      ConnectionStore connectionStore,
      ConnectionRecordGateway records,
      ConnectionLock lock,
      RecordLimits recordLimits,
      ExtensionSecretGateway extensionSecrets) {
    this.settingsRegistry = settingsRegistry;
    this.settingsGateway = settingsGateway;
    this.connectionStore = connectionStore;
    this.records = records;
    this.lock = lock;
    this.recordLimits = recordLimits;
    this.extensionSecrets = extensionSecrets;
  }

  /**
   * The context of a request to the extension on a live connection.
   *
   * @throws ConnectionManagementException when the connection's stored settings no longer resolve
   */
  ConnectionExtensionContext<Object> forInvocation(
      DataSource connection,
      IntegrationConnectionSchemaReference integration,
      String extensionId,
      UUID userId) {
    var setup = setupOf(connection);
    return new ExtensionInvocationContext(
        connection.id(),
        integration,
        effectiveSettings(connection, integration),
        setup,
        new SetupScopedSecrets(
            referencedSecrets(setup),
            connectionStore,
            HostedSecretEntry.writable(
                connection.id(), integration, extensionId, extensionSecrets, lock)),
        recordStore(connection, integration, extensionId, userId),
        userId);
  }

  /**
   * Reads now what the connection's extensions need once it is deleted: deleting it removes the
   * secret material its setup referenced, so that material is kept in memory for them.
   *
   * @throws ConnectionManagementException when the connection's stored settings no longer resolve
   */
  DeletedConnection captureBeforeDeletion(
      DataSource connection, IntegrationConnectionSchemaReference integration, UUID userId) {
    var setup = setupOf(connection);
    var refs = referencedSecrets(setup);
    var material = new HashMap<String, Map<String, String>>();
    for (var ref : refs) {
      var stored = connectionStore.read(ref);
      if (stored != null) {
        material.put(ref, Map.copyOf(stored));
      }
    }
    return new DeletedConnection(
        connection,
        integration,
        effectiveSettings(connection, integration),
        setup,
        refs,
        material,
        userId);
  }

  /** A deleted connection as its extensions see it once the deletion is final. */
  final class DeletedConnection {

    private final DataSource connection;
    private final IntegrationConnectionSchemaReference integration;
    private final Object settings;
    private final Optional<ConnectionSpec> setup;
    private final Set<String> referencedSecrets;
    private final Map<String, Map<String, String>> secretMaterial;
    private final UUID userId;

    private DeletedConnection(
        DataSource connection,
        IntegrationConnectionSchemaReference integration,
        Object settings,
        Optional<ConnectionSpec> setup,
        Set<String> referencedSecrets,
        Map<String, Map<String, String>> secretMaterial,
        UUID userId) {
      this.connection = connection;
      this.integration = integration;
      this.settings = settings;
      this.setup = setup;
      this.referencedSecrets = Set.copyOf(referencedSecrets);
      this.secretMaterial = Map.copyOf(secretMaterial);
      this.userId = userId;
    }

    ConnectionExtensionContext<Object> contextFor(String extensionId) {
      return new ExtensionInvocationContext(
          connection.id(),
          integration,
          settings,
          setup,
          new SetupScopedSecrets(
              referencedSecrets,
              secretMaterial::get,
              HostedSecretEntry.readOnly(
                  connection.id(), integration.integrationId(), extensionId, extensionSecrets)),
          new ReadOnlyRecordStore(recordStore(connection, integration, extensionId, userId)),
          userId);
    }
  }

  private ConfinedRecordStore recordStore(
      DataSource connection,
      IntegrationConnectionSchemaReference integration,
      String extensionId,
      UUID userId) {
    return new ConfinedRecordStore(
        new RecordScope(connection.id(), integration.integrationId(), extensionId),
        integration,
        userId,
        records,
        lock,
        recordLimits);
  }

  /** A connection the host provisions itself, rather than through a transport, has no setup. */
  private static Optional<ConnectionSpec> setupOf(DataSource connection) {
    return switch (connection) {
      case SocketDataSource socket -> Optional.of(socket.spec());
      case KafkaDataSource _ -> Optional.empty();
    };
  }

  private static Set<String> referencedSecrets(Optional<ConnectionSpec> setup) {
    return setup.map(ConnectionSpec::secretRefs).map(Set::copyOf).orElse(Set.of());
  }

  /**
   * The settings the connection's extensions run with. Stored overrides that no longer resolve,
   * after a deployment default changed for instance, leave the connection without any.
   */
  private Object effectiveSettings(
      DataSource connection, IntegrationConnectionSchemaReference integration) {
    var schema = settingsRegistry.find(integration).orElseThrow();
    return switch (settingsRegistry.resolve(schema, settingsGateway.find(connection.id()))) {
      case SettingsResolution.Resolved resolved -> resolved.effective();
      case SettingsResolution.Rejected _ ->
          throw new ConnectionManagementException(
              ConnectionExceptionCodes.CONNECTION_SETTINGS_UNRESOLVED);
    };
  }
}
