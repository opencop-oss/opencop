// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.persistence;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart.PendingRestart;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart.RestartBaseline;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionSetupReplacementPolicy;
import com.opencop.core.api.general.business.policy.connection.ListenPortExclusivityPolicy;
import com.opencop.core.api.general.business.usecase.connection.extension.ExtensionSecretEntries;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stores a setup that replaces a connection's own, then restarts the connection on it.
 *
 * <p>The setup is stored over the locked row in one transaction, after rechecking what a concurrent
 * change could have broken since it was resolved. Should that transaction, or the read before it,
 * fail, the setup's own material is compensated as on creation, and the replaced setup stays
 * untouched. Once it commits, an enabled connection restarts on it, and the secrets only the
 * replaced setup read are deleted, as are the secret entries of the extensions of an integration
 * the connection leaves.
 */
@Component
public class ConnectionSetupPersistence {

  private static final Logger log = LoggerFactory.getLogger(ConnectionSetupPersistence.class);

  private final ConnectionMetadataGateway connections;
  private final DataSourceRepository dataSources;
  private final ConnectionSetupRechecks rechecks;
  private final ConnectionIntegrationSettingsGateway settingsGateway;
  private final ConnectionRecordGateway records;
  private final ConnectorRestart connectorRestart;
  private final ConnectionStore connectionStore;
  private final ExtensionSecretEntries extensionSecretEntries;
  private final CompensatedTransaction transaction;

  public ConnectionSetupPersistence(
      ConnectionMetadataGateway connections,
      DataSourceRepository dataSources,
      ConnectionSetupRechecks rechecks,
      ConnectionIntegrationSettingsGateway settingsGateway,
      ConnectionRecordGateway records,
      ConnectorRestart connectorRestart,
      ConnectionStore connectionStore,
      ExtensionSecretEntries extensionSecretEntries,
      CompensatedTransaction transaction) {
    this.connections = connections;
    this.dataSources = dataSources;
    this.rechecks = rechecks;
    this.settingsGateway = settingsGateway;
    this.records = records;
    this.connectorRestart = connectorRestart;
    this.connectionStore = connectionStore;
    this.extensionSecretEntries = extensionSecretEntries;
    this.transaction = transaction;
  }

  /**
   * Stores the setup over the locked connection, then restarts it on the instance it is assigned
   * to. Unlike an orchestrated restart, no route-fresh instance is resolved first: the restart
   * epoch belongs to the source, so whichever instance runs it applies the restart. Only the brief
   * wait for the replacing connector depends on that instance, and runs out after its full wait,
   * reporting the earlier state, when the instance is gone.
   *
   * @param setup the resolved setup, of the connection's own transport kind
   * @param userId the user replacing the setup, recorded as its last updater
   */
  public void replaceAndRestart(UUID sourceId, ConnectionSetup setup, UUID userId) {
    replaceAndRestart(sourceId, setup, userId, _ -> {});
  }

  /**
   * As {@link #replaceAndRestart(UUID, ConnectionSetup, UUID)}, also running {@code inTransaction}
   * as the last step of the replacement's own transaction, with the connection as it reads under
   * the lock. A failure there rolls the replacement back with it.
   */
  public void replaceAndRestart(
      UUID sourceId,
      ConnectionSetup setup,
      UUID userId,
      Consumer<ConnectionMetadata> inTransaction) {
    RestartBaseline baseline;
    try {
      baseline = restartBaseline(sourceId);
    } catch (RuntimeException failure) {
      // Nothing references the setup's material before its transaction, so it is deleted here.
      setup.cleanup().ifPresent(cleanup -> transaction.compensate(sourceId, cleanup, failure));
      throw failure;
    }
    var replaced =
        transaction.runReplacement(
            sourceId,
            setup.spec().transport().kind(),
            setup.spec().secretRefs(),
            () -> store(sourceId, setup, userId, baseline, inTransaction),
            setup.cleanup());
    replaced.restart().ifPresent(connectorRestart::applyAndObserve);
    deleteRetired(sourceId, replaced.retiredRefs());
    replaced
        .leftSchema()
        .ifPresent(schema -> extensionSecretEntries.retire(sourceId, schema.integrationId()));
  }

  /**
   * The connector running before the setup is replaced, read before the transaction because the
   * read is best-effort. Its instance is read again under the lock, which discards the baseline if
   * the connection moved in between.
   */
  private RestartBaseline restartBaseline(UUID sourceId) {
    var instanceId =
        dataSources
            .findById(sourceId)
            .filter(SocketDataSource.class::isInstance)
            .map(SocketDataSource.class::cast)
            .map(SocketDataSource::assignedInstance)
            .orElse(null);
    return connectorRestart.baseline(sourceId, instanceId);
  }

  private Replaced store(
      UUID sourceId,
      ConnectionSetup setup,
      UUID userId,
      RestartBaseline baseline,
      Consumer<ConnectionMetadata> inTransaction) {
    var locked = connections.lockById(sourceId);
    ResourceLifecyclePolicy.assertDataSourceActive(
        locked.archivedAt(), ConnectionSetupReplacementPolicy.OPERATION);
    // Read under the lock, so a failover since the setup was resolved is seen, and the secrets
    // retired are those of the setup this one replaces.
    var previous = lockedSource(sourceId);
    rechecks.recheck(sourceId, setup.integration(), setup.access(), setup.spec());
    ConnectionSetupReplacementPolicy.assertRecordsKept(
        locked.integration(), setup.integration(), records.existsFor(sourceId));
    // The setup's settings replace the stored ones whole. Those name the integration they were
    // validated against, so they go before the setup can bind the connection to another one.
    settingsGateway.delete(sourceId);
    try {
      dataSources.updateSetup(sourceId, setup.stored(), userId);
    } catch (DataSourceListenPortConflictException e) {
      throw ListenPortExclusivityPolicy.portAlreadyReserved(e.listenPort());
    }
    SettingsOverrides.of(setup.settings())
        .ifPresent(overrides -> settingsGateway.save(sourceId, setup.integration(), overrides));
    inTransaction.accept(locked);
    var restart =
        locked.enabled()
            ? Optional.of(connectorRestart.request(sourceId, baseline, previous.assignedInstance()))
            : Optional.<PendingRestart>empty();
    var leftSchema =
        ConnectionSetupReplacementPolicy.changesIntegration(
                locked.integration(), setup.integration())
            ? Optional.of(locked.integration())
            : Optional.<IntegrationConnectionSchemaReference>empty();
    return new Replaced(
        restart,
        ConnectionSetupReplacementPolicy.retiredSecretRefs(previous.spec(), setup.spec()),
        leftSchema);
  }

  /** The connection's kind is unchanged by its setup, so it is still a socket connection. */
  private SocketDataSource lockedSource(UUID sourceId) {
    return dataSources
        .findById(sourceId)
        .map(SocketDataSource.class::cast)
        .orElseThrow(() -> new IllegalStateException("Locked source " + sourceId + " vanished"));
  }

  /**
   * Deletes the replaced setup's secrets after the commit, once an enabled connection's restart has
   * been requested and briefly observed. Connect does not fall back to them, so a connector still
   * running on them may fail one reconnect before it restarts; a failure to delete leaves them
   * behind without failing the replacement.
   */
  private void deleteRetired(UUID sourceId, List<String> retiredRefs) {
    if (retiredRefs.isEmpty()) {
      return;
    }
    try {
      connectionStore.delete(retiredRefs);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to delete the replaced setup's secrets {} for source {}",
          retiredRefs,
          sourceId,
          e);
    }
  }

  /**
   * What storing a setup leaves to do once it commits.
   *
   * @param restart the restart an enabled connection takes the setup up with
   * @param retiredRefs the secrets only the replaced setup read
   * @param leftSchema the schema of an integration the connection leaves, whose extensions' secret
   *     entries no extension reads again
   */
  private record Replaced(
      Optional<PendingRestart> restart,
      List<String> retiredRefs,
      Optional<IntegrationConnectionSchemaReference> leftSchema) {}
}
