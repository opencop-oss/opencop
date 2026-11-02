// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.model.connection.CurrentBrokerProvider;
import com.opencop.core.api.general.business.orchestrator.ConnectorRestart;
import com.opencop.core.api.general.business.orchestrator.RestartOrchestrator;
import com.opencop.core.api.general.business.policy.connection.ConnectionMetadataPatchPolicy;
import com.opencop.core.api.general.business.service.ConnectInstanceFailover;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.ConnectionArchiveLifecycleService;
import com.opencop.core.api.general.business.service.DataSourceAssignmentReconciler;
import com.opencop.core.api.general.business.service.DataSourceRuntimeService;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.delete.DeleteConnectionUsecase;
import com.opencop.core.api.general.business.usecase.connection.extension.ConnectionDeleteHooks;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.business.usecase.connection.patch.KafkaConnectionPatch;
import com.opencop.core.api.general.business.usecase.connection.patch.PatchConnectionUsecase;
import com.opencop.core.api.general.business.usecase.connection.patch.RtspConnectionPatch;
import com.opencop.core.api.general.business.usecase.connection.patch.TcpConnectionPatch;
import com.opencop.core.api.general.business.usecase.connection.patch.UdpConnectionPatch;
import com.opencop.core.api.general.business.usecase.connection.retrieve.RetrieveConnectionUsecase;
import com.opencop.core.api.general.business.usecase.connection.start.StartConnectionUsecase;
import com.opencop.core.api.general.business.usecase.connection.stop.StopConnectionUsecase;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.cache.EventGeoCacheEvictionGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.technical.gateway.ResourceActivityMaintenance;
import com.opencop.core.api.general.ports.usecase.connection.delete.DeleteConnection;
import com.opencop.core.api.general.ports.usecase.connection.patch.PatchConnection;
import com.opencop.core.api.general.ports.usecase.connection.restart.RestartConnection;
import com.opencop.core.api.general.ports.usecase.connection.retrieve.RetrieveConnection;
import com.opencop.core.api.general.ports.usecase.connection.start.StartConnection;
import com.opencop.core.api.general.ports.usecase.connection.stop.StopConnection;
import com.opencop.core.api.general.technical.gateway.kafka.ByodSourcePublisher;
import com.opencop.core.api.general.technical.gateway.reconciliation.DataSourceAssignmentReconciliationScheduler;
import com.opencop.core.api.general.technical.gateway.runtime.ConnectWorkerRunCleanupScheduler;
import com.opencop.core.api.general.technical.gateway.runtime.PostgresResourceActivityMaintenance;
import com.opencop.core.api.general.technical.gateway.runtime.ResourceActivityCleanupScheduler;
import com.opencop.core.api.general.technical.gateway.runtime.RuntimeObservationCleanupScheduler;
import com.opencop.core.api.general.technical.service.ConnectionAuthorizationService;
import com.opencop.core.api.general.technical.service.SourceRelationshipAuthorizationService;
import com.opencop.core.api.iam.ports.gateway.repository.ServerUserRepository;
import com.opencop.core.api.iam.ports.usecase.access.server.ServerManager;
import com.opencop.core.sdk.business.model.iam.access.Server;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthMaintenance;
import com.opencop.core.sdk.ports.gateway.api.SourceRestartEpochWriter;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationMaintenance;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationReader;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import jakarta.annotation.Nullable;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ConnectionConfig {

  @Bean
  ConnectionIntegrationSelection connectionIntegrationSelection(
      IntegrationCatalogGateway catalog, List<IntegrationConnectionSchema> schemas) {
    return new ConnectionIntegrationSelection(catalog, schemas);
  }

  @Bean
  ConnectionArchiveLifecycleService connectionArchiveLifecycle(
      SocketReconciliationNudgePublisher nudgePublisher,
      ProviderConfig providerConfig,
      DataSourceRepository dataSourceRepository,
      ConnectionStore connectionStore,
      EventGeoCacheEvictionGateway eventGeoCacheEvictionGateway,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourcePublisher byodSourcePublisher,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    return new ConnectionArchiveLifecycleService(
        nudgePublisher,
        new CurrentBrokerProvider(providerConfig.name()),
        dataSourceRepository,
        connectionStore,
        eventGeoCacheEvictionGateway,
        kafkaProvisioner,
        byodSourcePublisher,
        auditRecorder,
        projection,
        transactionManager);
  }

  @Bean
  DeleteConnection deleteConnection(
      ConnectionAuthorization authorization,
      ServerManager<Server> serverManager,
      ConnectionArchiveLifecycleService archiveLifecycle,
      ConnectionDeleteHooks deleteHooks,
      ConnectionLock connectionLock) {
    return new DeleteConnectionUsecase(
        authorization, serverManager, archiveLifecycle, deleteHooks, connectionLock);
  }

  @Bean
  RetrieveConnection retrieveConnection(
      DataSourceRepository dataSourceRepository,
      DataSourceRuntimeService runtimeService,
      ProviderConfig providerConfig,
      ConnectionIntegrationSettingsGateway settingsGateway,
      ConnectionSettingsRegistry settingsRegistry) {
    return new RetrieveConnectionUsecase(
        dataSourceRepository, runtimeService, providerConfig, settingsGateway, settingsRegistry);
  }

  @Bean
  PatchConnection patchConnection(
      ConnectionAuthorization connectionAuthorizationService,
      ConnectionMetadataGateway gateway,
      ConnectionMetadataPatchPolicy policy,
      DataSourceRepository dataSourceRepository,
      ConnectionIntegrationSelection integrationSelection,
      ConnectionMappingRegistry mappingRegistry,
      SocketReconciliationNudgePublisher nudgePublisher,
      TransactionTemplate transactionTemplate,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    return new PatchConnectionUsecase(
        connectionAuthorizationService,
        gateway,
        policy,
        integrationSelection,
        List.of(
            new TcpConnectionPatch(dataSourceRepository, mappingRegistry),
            new UdpConnectionPatch(),
            new RtspConnectionPatch(),
            new KafkaConnectionPatch()),
        nudgePublisher,
        transactionTemplate,
        auditRecorder,
        projection);
  }

  @Bean
  ConnectionMetadataPatchPolicy connectionMetadataPatchPolicy() {
    return new ConnectionMetadataPatchPolicy();
  }

  @Bean
  ConnectionAuthorization connectionAuthorizationService(
      DataSourceRepository dataSourceRepository,
      ServerUserRepository serverUserRepository,
      SourceRelationshipAuthorizationService sourceAuthorization) {
    return new ConnectionAuthorizationService(
        dataSourceRepository, serverUserRepository, sourceAuthorization);
  }

  @Bean
  ConnectRoutingTargetResolver connectRoutingTargetResolver(
      @Qualifier("connectInstanceResolver") ConnectInstanceResolver instanceResolver) {
    return new ConnectRoutingTargetResolver(instanceResolver);
  }

  @Bean
  ConnectInstanceFailover connectInstanceFailover(
      SocketReconciliationNudgePublisher nudgePublisher,
      ConnectRoutingTargetResolver routingTargetResolver,
      DataSourceRepository dataSourceRepository,
      DataSourceRuntimeService runtimeService) {
    return new ConnectInstanceFailover(
        nudgePublisher, routingTargetResolver, dataSourceRepository, runtimeService);
  }

  @Bean
  DataSourceAssignmentReconciler dataSourceAssignmentReconciler(
      DataSourceRepository dataSourceRepository,
      ConnectRoutingTargetResolver routingTargetResolver,
      SocketReconciliationNudgePublisher nudgePublisher) {
    return new DataSourceAssignmentReconciler(
        dataSourceRepository, routingTargetResolver, nudgePublisher);
  }

  @Bean
  DataSourceAssignmentReconciliationScheduler dataSourceAssignmentReconciliationScheduler(
      DataSourceAssignmentReconciler reconciler,
      CoreProperties coreProperties,
      DataSourceStartupCoordinator startupCoordinator,
      StartupLeader startupLeader) {
    return new DataSourceAssignmentReconciliationScheduler(
        reconciler,
        coreProperties.getAssignmentReconciliation(),
        startupCoordinator,
        startupLeader);
  }

  @Bean
  RuntimeObservationCleanupScheduler runtimeObservationCleanupScheduler(
      @Qualifier("sourceRuntimeObservationMaintenance")
          SourceRuntimeObservationMaintenance maintenance,
      CoreProperties coreProperties,
      StartupLeader startupLeader) {
    return new RuntimeObservationCleanupScheduler(
        maintenance, coreProperties.getRuntimeObservationCleanup(), startupLeader);
  }

  @Bean
  ResourceActivityMaintenance resourceActivityMaintenance(NamedParameterJdbcTemplate jdbc) {
    return new PostgresResourceActivityMaintenance(jdbc);
  }

  @Bean
  ResourceActivityCleanupScheduler resourceActivityCleanupScheduler(
      ResourceActivityMaintenance maintenance,
      CoreProperties coreProperties,
      StartupLeader startupLeader) {
    return new ResourceActivityCleanupScheduler(
        maintenance, coreProperties.getResourceActivityCleanup(), startupLeader);
  }

  @Bean
  ConnectWorkerRunCleanupScheduler connectWorkerRunCleanupScheduler(
      @Qualifier("connectWorkerHealthMaintenance") ConnectWorkerHealthMaintenance maintenance,
      CoreProperties coreProperties,
      StartupLeader startupLeader) {
    return new ConnectWorkerRunCleanupScheduler(
        maintenance, coreProperties.getConnectWorkerRunCleanup(), startupLeader);
  }

  @Bean
  StartConnection startConnection(
      ConnectionAuthorization connectionAuthorizationService,
      DataSourceRepository dataSourceRepository,
      ConnectionMetadataGateway connectionMetadataGateway,
      ConnectInstanceFailover instanceFailover,
      ProviderConfig providerConfig,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourcePublisher byodSourcePublisher,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    return new StartConnectionUsecase(
        connectionAuthorizationService,
        dataSourceRepository,
        instanceFailover,
        connectionMetadataGateway,
        new CurrentBrokerProvider(providerConfig.name()),
        kafkaProvisioner,
        byodSourcePublisher,
        auditRecorder,
        projection,
        transactionManager);
  }

  @Bean
  StopConnection stopConnection(
      ConnectionAuthorization connectionAuthorizationService,
      SocketReconciliationNudgePublisher nudgePublisher,
      DataSourceRepository dataSourceRepository,
      ConnectionMetadataGateway connectionMetadataGateway,
      ProviderConfig providerConfig,
      KafkaProvisioningService kafkaProvisioner,
      ByodSourcePublisher byodSourcePublisher,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection,
      PlatformTransactionManager transactionManager) {
    return new StopConnectionUsecase(
        connectionAuthorizationService,
        nudgePublisher,
        dataSourceRepository,
        connectionMetadataGateway,
        new CurrentBrokerProvider(providerConfig.name()),
        kafkaProvisioner,
        byodSourcePublisher,
        auditRecorder,
        projection,
        transactionManager);
  }

  @Bean
  ConnectorRestart connectorRestart(
      SourceRestartEpochWriter restartEpochWriter,
      SocketReconciliationNudgePublisher nudgePublisher,
      @Nullable @Qualifier("sourceRuntimeObservationReader")
          SourceRuntimeObservationReader sourceRuntimeObservationReader) {
    return new ConnectorRestart(restartEpochWriter, nudgePublisher, sourceRuntimeObservationReader);
  }

  @Bean
  RestartConnection restartConnection(
      DataSourceRepository dataSourceRepository,
      ConnectRoutingTargetResolver routingTargetResolver,
      ConnectorRestart connectorRestart) {
    return new RestartOrchestrator(dataSourceRepository, routingTargetResolver, connectorRestart);
  }

  @Bean
  DataSourceRuntimeService dataSourceRuntimeService(
      DataSourceRepository dataSourceRepository,
      ProviderConfig providerConfig,
      KafkaProvisioningService kafkaProvisioner,
      @Nullable @Qualifier("sourceRuntimeObservationReader")
          SourceRuntimeObservationReader sourceRuntimeObservationReader,
      @Nullable @Qualifier("connectInstanceResolver")
          ConnectInstanceResolver connectInstanceResolver) {
    return new DataSourceRuntimeService(
        dataSourceRepository,
        new CurrentBrokerProvider(providerConfig.name()),
        kafkaProvisioner,
        sourceRuntimeObservationReader,
        connectInstanceResolver);
  }
}
