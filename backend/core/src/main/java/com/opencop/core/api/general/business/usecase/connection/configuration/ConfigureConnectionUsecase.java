// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.configuration;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.model.connection.configuration.ConnectionConfigurationChange;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionSettingsPolicy;
import com.opencop.core.api.general.business.policy.connection.TcpMapperFramingPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AccessModeFormatPolicy;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.policy.connection.access.ConnectionAccessPolicy;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.api.general.ports.usecase.connection.configuration.ConfigureConnection;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Changes a connection's access mode with the mappers it reads and writes with, and its integration
 * settings overrides, while it runs.
 *
 * <p>The change is checked exactly as a creation checks the same selection, against what the
 * connection already stores: its transport, its integration's mapping declaration and, for TCP, its
 * framing. The connection's integration must still be usable. Once the change commits, Connect is
 * woken to apply it to the running connector in place; nothing restarts it.
 */
@Component
public class ConfigureConnectionUsecase implements ConfigureConnection {

  private static final String OPERATION = "configure";

  private final ConnectionAuthorization authorization;
  private final ConnectionMetadataGateway connections;
  private final DataSourceRepository dataSources;
  private final ConnectionIntegrationSelection integrationSelection;
  private final ConnectionMappingRegistry mappingRegistry;
  private final ConnectionMapperSelection mapperSelection;
  private final ConnectionSettingsRegistry settingsRegistry;
  private final ConnectionIntegrationSettingsGateway settingsGateway;
  private final SelectedMapperLock selectedMapperLock;
  private final ConnectInstanceFailover instanceFailover;
  private final RetrieveConnection retrieveConnection;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;
  private final TransactionTemplate transactionTemplate;

  public ConfigureConnectionUsecase(
      ConnectionAuthorization authorization,
      ConnectionMetadataGateway connections,
      DataSourceRepository dataSources,
      ConnectionIntegrationSelection integrationSelection,
      ConnectionMappingRegistry mappingRegistry,
      ConnectionMapperSelection mapperSelection,
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionIntegrationSettingsGateway settingsGateway,
      SelectedMapperLock selectedMapperLock,
      ConnectInstanceFailover instanceFailover,
      RetrieveConnection retrieveConnection,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      TransactionTemplate transactionTemplate) {
    this.authorization = authorization;
    this.connections = connections;
    this.dataSources = dataSources;
    this.integrationSelection = integrationSelection;
    this.mappingRegistry = mappingRegistry;
    this.mapperSelection = mapperSelection;
    this.settingsRegistry = settingsRegistry;
    this.settingsGateway = settingsGateway;
    this.selectedMapperLock = selectedMapperLock;
    this.instanceFailover = instanceFailover;
    this.retrieveConnection = retrieveConnection;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public ConnectionInfo configure(
      UUID sourceId, ConnectionConfigurationChange change, Caller actor) {
    var userId = actor.userId();
    authorization.assertDirectOwnerOrAdmin(sourceId, userId, actor.globalAdmin());
    if (!change.changesAccess() && !change.changesSettings()) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.NO_FIELDS_TO_UPDATE);
    }
    var mode =
        change.changesAccess()
            ? Optional.of(requireAccessMode(change))
            : Optional.<AccessMode>empty();

    transactionTemplate.executeWithoutResult(
        _ -> {
          var current = connections.lockById(sourceId);
          ResourceLifecyclePolicy.assertDataSourceActive(current.archivedAt(), OPERATION);
          // Everything is checked before anything is written, so a rejection stores neither part.
          var access = mode.map(selected -> resolveAccess(current, change, selected));
          var settings = change.settings().map(document -> resolveSettings(current, document));
          access.ifPresent(
              selected -> {
                // Read under the same lock and before the write, so a re-saved form that selects
                // the mappers already stored is told apart from a real swap.
                var beforeEgress = storedEgressMapperId(sourceId);
                selectedMapperLock.hold(selected);
                connections.applyAccess(sourceId, selected, userId);
                recordMapperChange(sourceId, current, beforeEgress, selected, actor);
              });
          settings.ifPresent(resolved -> storeSettings(current, resolved));
        });

    instanceFailover.publishAndAwaitSettled(sourceId);
    return retrieveConnection.get(sourceId, userId);
  }

  /**
   * Records the mappers the change actually moved, inside the transaction that moved them.
   *
   * <p>Settings carry no row of their own: they are plugin-declared values with no sensitivity
   * marker, and the access change beside them is what a reader is looking for.
   */
  private void recordMapperChange(
      UUID sourceId,
      ConnectionMetadata current,
      UUID beforeEgress,
      ConnectionAccess selected,
      Caller actor) {
    var afterIngress = selected.ingressMapperId().orElse(null);
    var afterEgress = selected.egressMapperId().orElse(null);
    if (Objects.equals(current.ingressMapperId(), afterIngress)
        && Objects.equals(beforeEgress, afterEgress)) {
      return;
    }
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            current.name(),
            Operation.MAPPER_CHANGED,
            actor,
            projection.mapperChanged(
                current.ingressMapperId(), afterIngress, beforeEgress, afterEgress),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  /** The mapper the connection writes with today; only a socket connection has one. */
  private UUID storedEgressMapperId(UUID sourceId) {
    return dataSources
        .findById(sourceId)
        .map(source -> source instanceof SocketDataSource socket ? socket.egressMapperId() : null)
        .orElse(null);
  }

  /** Once any access field is sent, the access mode is required, exactly as on creation. */
  private static AccessMode requireAccessMode(ConnectionConfigurationChange change) {
    if (change.access() == null) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.REQUIRED_FIELD_INVALID,
          ConnectionCreationDetails.ACCESS_FIELD,
          ConnectionCreationDetails.ACCESS_FIELD);
    }
    return AccessModeFormatPolicy.parse(change.access());
  }

  /**
   * The access the change selects, once the connection's transport allows its mode, its integration
   * is still usable and permits its mappers, and those mappers can be selected. Checked in the
   * order a creation checks them.
   */
  private ConnectionAccess resolveAccess(
      ConnectionMetadata current, ConnectionConfigurationChange change, AccessMode mode) {
    AllowedAccessModesPolicy.assertTransportAllows(current.transportKind(), mode);
    var access =
        ConnectionAccessPolicy.resolveSelection(mode, change.mapperId(), change.egressMapperId());
    var integration = requireUsableIntegration(current);
    var protocol = AllowedAccessModesPolicy.protocol(current.transportKind());
    var declaration =
        ConnectionIntegrationReferencePolicy.requireDeclaration(
            mappingRegistry.find(integration, protocol), integration, protocol.name());
    switch (declaration) {
      case MapperFramingDeclaration framed -> requireFramedMappers(current, framed, access);
      case MapperDeclaration mapperOnly ->
          access
              .ingressMapperId()
              .ifPresent(mapperId -> mapperSelection.ingress(mapperOnly, mapperId));
    }
    return access;
  }

  /**
   * Requires a declaration that pairs mappers with framings to permit the mappers with the framing
   * the connection already splits its stream with. A connection storing no framing has no pair an
   * ingress mapper could be checked against, so it cannot take one.
   */
  private void requireFramedMappers(
      ConnectionMetadata current, MapperFramingDeclaration declaration, ConnectionAccess access) {
    var framing = TcpMapperFramingPolicy.resolved(storedFraming(current.id()));
    TcpMapperFramingPolicy.assertSelection(declaration, access.ingressMapperId(), framing);
    access
        .ingressMapperId()
        .ifPresent(
            mapperId -> mapperSelection.ingress(declaration, mapperId, framing.orElseThrow()));
    access.egressMapperId().ifPresent(mapperId -> mapperSelection.egress(declaration, mapperId));
  }

  /**
   * The overrides the change stores, normalised, once they resolve against the integration's
   * declared settings exactly as on creation. A clear is always accepted, even for a connection
   * whose integration can no longer be used: it leaves the connection on the deployment defaults.
   */
  private SettingsDocument resolveSettings(ConnectionMetadata current, Map<String, ?> document) {
    if (document.isEmpty()) {
      return SettingsDocument.empty();
    }
    var integration = requireUsableIntegration(current);
    var resolved =
        ConnectionSettingsPolicy.requireResolved(settingsRegistry.resolve(integration, document));
    ConnectionSettingsPolicy.assertTransportAccepts(current.transportKind(), resolved);
    return resolved;
  }

  /** Replaces the connection's overrides, or deletes them when none remain. */
  private void storeSettings(ConnectionMetadata current, SettingsDocument resolved) {
    SettingsOverrides.of(resolved)
        .ifPresentOrElse(
            overrides -> settingsGateway.save(current.id(), current.integration(), overrides),
            () -> settingsGateway.delete(current.id()));
  }

  /**
   * The connection's integration, locked against concurrent deactivation, once it can still be
   * used. Startup binds every connection stored without an integration to Native, so one always
   * names the integration its configuration is checked against.
   */
  private IntegrationConnectionSchemaReference requireUsableIntegration(
      ConnectionMetadata current) {
    var integration =
        Objects.requireNonNull(
            current.integration(), () -> "Connection " + current.id() + " names no integration");
    integrationSelection.requireSelectableForUpdate(integration);
    return integration;
  }

  private TcpFraming storedFraming(UUID sourceId) {
    return dataSources
        .findById(sourceId)
        .map(
            source ->
                source instanceof SocketDataSource socket
                        && socket.spec().transport() instanceof TcpConfig tcp
                    ? tcp.framing()
                    : null)
        .orElse(null);
  }
}
