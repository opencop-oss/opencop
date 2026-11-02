// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.retrieve;

import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsViolation;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.model.connection.ConnectionSettingsStatus;
import com.opencop.core.sdk.model.connection.ConnectionSetupInfo;
import com.opencop.core.sdk.model.connection.add.SecurityKind;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceRuntime;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.ErrorDetail;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class RetrieveConnectionUsecase implements RetrieveConnection {

  private static final Logger log = LoggerFactory.getLogger(RetrieveConnectionUsecase.class);

  private final DataSourceRepository dataSourceRepository;
  private final DataSourceRuntimeService runtimeService;
  private final ProviderConfig providerConfig;
  private final ConnectionIntegrationSettingsGateway settingsGateway;
  private final ConnectionSettingsRegistry settingsRegistry;

  public RetrieveConnectionUsecase(
      DataSourceRepository dataSourceRepository,
      DataSourceRuntimeService runtimeService,
      ProviderConfig providerConfig,
      ConnectionIntegrationSettingsGateway settingsGateway,
      ConnectionSettingsRegistry settingsRegistry) {
    this.dataSourceRepository = dataSourceRepository;
    this.runtimeService = runtimeService;
    this.providerConfig = providerConfig;
    this.settingsGateway = settingsGateway;
    this.settingsRegistry = settingsRegistry;
  }

  @Override
  public ConnectionInfo get(UUID sourceId, UUID userId) {
    // Connection authorization is not enforced here yet, so this lookup allows any caller.

    var dataSource =
        dataSourceRepository
            .findByIdForProvider(sourceId, providerConfig.name())
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Data source not found: " + sourceId, HttpStatus.NOT_FOUND));

    var dsr = runtimeService.getWithRuntime(dataSource.id(), RecordVisibility.ALL);
    var runtime = dsr != null ? dsr.runtime() : RuntimeStatus.stopped(dataSource.id());
    return toConnectionInfo(
        dataSource,
        runtime,
        settingsGateway.find(dataSource.id()),
        dataSourceRepository.findSetupPrefill(dataSource.id()));
  }

  @Override
  public List<ConnectionInfo> getAll(UUID userId, RecordVisibility recordVisibility) {
    // Connection authorization is not enforced here yet, so this listing allows any caller.

    var dataSources = dataSourceRepository.findAll(providerConfig.name(), recordVisibility);
    var allIds = dataSources.stream().map(DataSource::id).collect(Collectors.toSet());
    var runtimeMap =
        allIds.isEmpty()
            ? Map.<UUID, DataSourceRuntime>of()
            : runtimeService.getWithRuntime(allIds, recordVisibility);
    var settingsById =
        allIds.isEmpty() ? Map.<UUID, SettingsOverrides>of() : settingsGateway.findAll(allIds);
    var prefillById =
        allIds.isEmpty()
            ? Map.<UUID, SetupPrefill>of()
            : dataSourceRepository.findSetupPrefills(allIds);

    return dataSources.stream()
        .map(
            ds -> {
              var dsr = runtimeMap.get(ds.id());
              var runtime = dsr != null ? dsr.runtime() : RuntimeStatus.stopped(ds.id());
              return toConnectionInfo(
                  ds,
                  runtime,
                  Optional.ofNullable(settingsById.get(ds.id())),
                  Optional.ofNullable(prefillById.get(ds.id())));
            })
        .toList();
  }

  /** The settings a retrieved connection reports, and whether its stored overrides resolve. */
  private record ReportedSettings(
      Map<String, Map<String, Object>> effective,
      ConnectionSettingsStatus status,
      List<ErrorDetail> violations) {

    static final ReportedSettings NONE =
        new ReportedSettings(null, ConnectionSettingsStatus.NONE, List.of());
  }

  /**
   * Resolves the effective settings of a connection from its stored overrides. A stored document
   * that no longer resolves, after a deployment default changed for instance, has none and is
   * reported as rejected with its violations. A connection whose schema version this runtime does
   * not register, or that declares no settings, has none to resolve.
   */
  private ReportedSettings reportedSettings(DataSource ds, Optional<SettingsOverrides> stored) {
    var schema = Optional.ofNullable(ds.integration()).flatMap(settingsRegistry::find);
    if (schema.isEmpty()) {
      return ReportedSettings.NONE;
    }
    var declaresSettings = !settingsRegistry.descriptor(schema.orElseThrow()).sections().isEmpty();
    var resolution = settingsRegistry.resolve(schema.orElseThrow(), stored);
    return switch (resolution) {
      case SettingsResolution.Resolved resolved ->
          new ReportedSettings(
              resolved.complete().plainValues(),
              declaresSettings ? ConnectionSettingsStatus.RESOLVED : ConnectionSettingsStatus.NONE,
              List.of());
      case SettingsResolution.Rejected rejected -> {
        log.debug(
            "Stored integration settings of connection {} no longer resolve: {}",
            ds.id(),
            rejected.violations());
        yield new ReportedSettings(
            null,
            ConnectionSettingsStatus.REJECTED,
            rejected.violations().stream().map(SettingsViolation::toErrorDetail).toList());
      }
    };
  }

  /**
   * @param stored the integration settings overrides the connection stores, if any
   * @param prefill the setup prefill the connection stores, if any
   */
  private ConnectionInfo toConnectionInfo(
      DataSource ds,
      RuntimeStatus runtime,
      Optional<SettingsOverrides> stored,
      Optional<SetupPrefill> prefill) {
    var settings = reportedSettings(ds, stored);
    var storedSettings = stored.map(SettingsOverrides::sections).orElse(null);
    return switch (ds) {
      case SocketDataSource socket ->
          new ConnectionInfo(
              ds.id(),
              ds.name(),
              ds.integration(),
              runtime.state(),
              runtime.stateReason(),
              ds.provider(),
              socket.spec().securityType(),
              ds.createdAt(),
              ds.createdBy(),
              ds.archivedAt(),
              ds.archivedBy(),
              ds.updatedAt(),
              runtime.lastMessageAt(),
              ds.updatedAt(),
              ds.updatedBy(),
              ds.lastEventAt(),
              socket.assignedInstance(),
              ds.defaultVisible(),
              ds.access(),
              storedSettings,
              settings.effective(),
              settings.status(),
              settings.violations(),
              setup(ds, prefill));
      case KafkaDataSource _ ->
          new ConnectionInfo(
              ds.id(),
              ds.name(),
              ds.integration(),
              runtime.state(),
              runtime.stateReason(),
              ds.provider(),
              SecurityKind.SCRAM,
              ds.createdAt(),
              ds.createdBy(),
              ds.archivedAt(),
              ds.archivedBy(),
              ds.updatedAt(),
              runtime.lastMessageAt(),
              ds.updatedAt(),
              ds.updatedBy(),
              ds.lastEventAt(),
              null,
              ds.defaultVisible(),
              ds.access(),
              storedSettings,
              settings.effective(),
              settings.status(),
              settings.violations(),
              setup(ds, prefill));
    };
  }

  /**
   * The connection's setup in the shape a create request for its kind takes. Its mappers and
   * framing are read from the connection itself; its extractor and text values only exist when its
   * setup was captured, which a connection created before setups were captured has not.
   */
  private static ConnectionSetupInfo setup(DataSource ds, Optional<SetupPrefill> prefill) {
    var integration =
        ds.integration() == null
            ? null
            : new ConnectionSetupInfo.SetupIntegration(
                ds.integration().integrationId(), ds.integration().schemaVersion());
    var extractorId = prefill.map(SetupPrefill::extractorId).orElse(null);
    var values = prefill.map(SetupPrefill::values).orElse(Map.of());
    return switch (ds) {
      case SocketDataSource socket ->
          switch (socket.spec().transport()) {
            case TcpConfig tcp ->
                new ConnectionSetupInfo.TcpConnectionSetupInfo(
                    integration,
                    extractorId,
                    values,
                    socket.ingressMapperId(),
                    socket.egressMapperId(),
                    framing(tcp));
            case UdpConfig _ ->
                new ConnectionSetupInfo.UdpConnectionSetupInfo(
                    integration, extractorId, values, socket.ingressMapperId());
            case RtspConfig _ ->
                new ConnectionSetupInfo.RtspConnectionSetupInfo(
                    integration, extractorId, values, socket.ingressMapperId());
          };
      case KafkaDataSource kafka ->
          new ConnectionSetupInfo.KafkaConnectionSetupInfo(
              integration, extractorId, values, kafka.ingressMapperId());
    };
  }

  /** How a TCP connection splits its stream, as a create request selects it. */
  private static ConnectionSetupInfo.SetupFraming framing(TcpConfig tcp) {
    if (tcp.framing() == null) {
      return null;
    }
    return switch (tcp.framing()) {
      case CustomTcpFraming custom ->
          new ConnectionSetupInfo.SetupFraming.CustomSetupFraming(
              custom.mode().mode(), custom.delimiter());
      case IntegrationTcpFraming integration ->
          new ConnectionSetupInfo.SetupFraming.IntegrationSetupFraming(integration.framingId());
    };
  }
}
