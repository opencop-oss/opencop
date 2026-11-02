// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.setup;

import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry.EntityType;
import com.opencop.core.api.general.business.model.audit.AuditEntry.Operation;
import com.opencop.core.api.general.business.model.audit.AuditEvent;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreation;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.business.model.connection.setup.ConnectionSetup;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionSetupReplacementPolicy;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.persistence.ConnectionSetupPersistence;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.api.general.ports.usecase.connection.setup.ReplaceConnectionSetup;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputResource;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInputValue;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Replaces how a connection connects, for example its endpoint, its credentials or the integration
 * it binds to, while it keeps its identity, name, visibility and permissions.
 *
 * <p>The new setup is checked exactly as a creation checks it, against the integration it requests:
 * the stored integration need not still be usable, so a setup can move a connection off one that no
 * longer is. It then replaces the stored setup whole, settings included, and the connection
 * restarts on it.
 */
@Component
public class ReplaceConnectionSetupUsecase implements ReplaceConnectionSetup {

  private final ConnectionAuthorization authorization;
  private final DataSourceRepository dataSources;
  private final ConnectionSetupResolution setupResolution;
  private final ConnectionSetupPersistence setupPersistence;
  private final RetrieveConnection retrieveConnection;
  private final AuditRecorder auditRecorder;
  private final DataSourceAuditProjection projection;

  public ReplaceConnectionSetupUsecase(
      ConnectionAuthorization authorization,
      DataSourceRepository dataSources,
      ConnectionSetupResolution setupResolution,
      ConnectionSetupPersistence setupPersistence,
      RetrieveConnection retrieveConnection,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    this.authorization = authorization;
    this.dataSources = dataSources;
    this.setupResolution = setupResolution;
    this.setupPersistence = setupPersistence;
    this.retrieveConnection = retrieveConnection;
    this.auditRecorder = auditRecorder;
    this.projection = projection;
  }

  @Override
  public ConnectionInfo replace(UUID sourceId, ConnectionCreation setup, Caller caller) {
    // One identity for both jobs: the caller this authorizes is the caller the row names. The two
    // used to be separate copies with nothing forcing them to agree.
    authorization.assertDirectOwnerOrAdmin(sourceId, caller.userId(), caller.globalAdmin());
    requireReplaceable(sourceId, setup);
    setupPersistence.replaceAndRestart(
        sourceId,
        resolve(setup, sourceId),
        caller.userId(),
        locked -> recordSetupReplaced(sourceId, locked.name(), setup));
    return retrieveConnection.get(sourceId, caller.userId());
  }

  /** Requires the stored connection to be active and its setup replaceable by this one. */
  private void requireReplaceable(UUID sourceId, ConnectionCreation setup) {
    var current =
        dataSources
            .findById(sourceId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Data source not found: " + sourceId, HttpStatus.NOT_FOUND));
    ResourceLifecyclePolicy.assertDataSourceActive(
        current, ConnectionSetupReplacementPolicy.OPERATION);
    ConnectionSetupReplacementPolicy.requireReplaceable(current, setup);
  }

  /**
   * Runs inside the replacement's own transaction, so the row commits or rolls back with the stored
   * setup. The name is the one read under the connection's lock, not before it.
   */
  private void recordSetupReplaced(UUID sourceId, String name, ConnectionCreation setup) {
    var details = setup.details();
    auditRecorder.record(
        new AuditEvent(
            EntityType.DATA_SOURCE,
            sourceId,
            name,
            Operation.SETUP_REPLACED,
            details.requestedBy(),
            projection.setupReplaced(fieldNames(setup), settingNames(details)),
            AuditEvent.CURRENT_CHANGE_VERSION));
  }

  /**
   * The names of the setup fields the request supplied, never their values: the extractor's own
   * fields come from a plugin descriptor and may be secrets or files, and the structural selections
   * beside them are only meaningful as "this moved".
   */
  private static Set<String> fieldNames(ConnectionCreation setup) {
    var details = setup.details();
    var names = new LinkedHashSet<String>();
    addIfPresent(names, ConnectionCreationDetails.ACCESS_FIELD, details.access());
    addIfPresent(names, ConnectionCreationDetails.INTEGRATION_FIELD, details.integration());
    addIfPresent(names, ConnectionCreationDetails.EXTRACTOR_FIELD, details.extractorId());
    addIfPresent(names, ConnectionCreationDetails.INGRESS_MAPPER_FIELD, details.ingressMapperId());
    if (setup instanceof ConnectionCreation.Tcp tcp) {
      addIfPresent(names, TcpFramingSelection.FIELD, tcp.framing());
      addIfPresent(names, ConnectionCreationDetails.EGRESS_MAPPER_FIELD, tcp.egressMapperId());
    }
    details.input().values().stream().map(ConnectionInputValue::name).forEach(names::add);
    details.input().resources().stream().map(ConnectionInputResource::role).forEach(names::add);
    return names;
  }

  private static List<String> settingNames(ConnectionCreationDetails details) {
    return details.settings() == null ? List.of() : List.copyOf(details.settings().keySet());
  }

  private static void addIfPresent(Set<String> names, String name, Object value) {
    if (!Objects.isNull(value)) {
      names.add(name);
    }
  }

  private ConnectionSetup resolve(ConnectionCreation setup, UUID sourceId) {
    return switch (setup) {
      case ConnectionCreation.Tcp tcp -> setupResolution.resolve(tcp, sourceId);
      case ConnectionCreation.Udp udp -> setupResolution.resolve(udp, sourceId);
      case ConnectionCreation.Rtsp rtsp -> setupResolution.resolve(rtsp, sourceId);
      case ConnectionCreation.Kafka _ ->
          throw new IllegalStateException("A Kafka connection's setup is never replaced");
    };
  }
}
