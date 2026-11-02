// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.connection.extension.ConnectionDeleteHooks;
import com.opencop.core.api.general.business.usecase.connection.extension.ExtensionConnections;
import com.opencop.core.api.general.business.usecase.connection.extension.ExtensionContexts;
import com.opencop.core.api.general.business.usecase.connection.extension.ExtensionSecretEntries;
import com.opencop.core.api.general.business.usecase.connection.extension.InvokeConnectionExtensionUsecase;
import com.opencop.core.api.general.business.usecase.connection.extension.ListConnectionExtensionsUsecase;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookRunner;
import com.opencop.core.api.general.ports.gateway.connection.extension.DeleteHookTelemetry;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.extension.InvokeConnectionExtension;
import com.opencop.core.api.general.ports.usecase.connection.extension.ListConnectionExtensions;
import com.opencop.core.api.general.technical.gateway.connection.extension.ExecutorDeleteHookRunner;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the serving of integrations' connection extensions. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConnectionExtensionProperties.class)
public class ConnectionExtensionConfiguration {

  @Bean
  ExtensionConnections extensionConnections(
      DataSourceRepository dataSources,
      ConnectionAuthorization authorization,
      List<IntegrationConnectionSchema> schemas) {
    return new ExtensionConnections(dataSources, authorization, schemas);
  }

  @Bean
  InvokeConnectionExtension invokeConnectionExtension(
      ExtensionConnections extensionConnections,
      ConnectionAuthorization authorization,
      ConnectionExtensionRegistry extensions,
      ExtensionContexts contexts) {
    return new InvokeConnectionExtensionUsecase(
        extensionConnections, authorization, extensions, contexts);
  }

  @Bean
  ListConnectionExtensions listConnectionExtensions(
      ExtensionConnections extensionConnections,
      ConnectionAuthorization authorization,
      ConnectionExtensionRegistry extensions) {
    return new ListConnectionExtensionsUsecase(extensionConnections, authorization, extensions);
  }

  @Bean
  ConnectionExtensionOpenApiCustomizer connectionExtensionOpenApiCustomizer(
      ConnectionExtensionRegistry extensions) {
    return new ConnectionExtensionOpenApiCustomizer(extensions);
  }

  @Bean
  ExtensionContexts extensionContexts(
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionIntegrationSettingsGateway settingsGateway,
      ConnectionStore connectionStore,
      ConnectionRecordGateway records,
      ConnectionLock connectionLock,
      ConnectionExtensionProperties properties,
      ExtensionSecretGateway extensionSecrets) {
    return new ExtensionContexts(
        settingsRegistry,
        settingsGateway,
        connectionStore,
        records,
        connectionLock,
        properties.toLimits(),
        extensionSecrets);
  }

  /**
   * Runs each delete hook apart, so the deletion can stop waiting for one that runs too long. On
   * shutdown, running hooks are interrupted rather than awaited: one ignoring interrupts must not
   * hold the shutdown.
   */
  @Bean(destroyMethod = "shutdownNow")
  ExecutorService connectionDeleteHookExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  DeleteHookRunner deleteHookRunner(ExecutorService connectionDeleteHookExecutor) {
    return new ExecutorDeleteHookRunner(connectionDeleteHookExecutor);
  }

  @Bean
  ExtensionSecretEntries extensionSecretEntries(
      ConnectionExtensionRegistry extensions,
      ExtensionSecretGateway extensionSecrets,
      ConnectionLock connectionLock,
      DeleteHookTelemetry telemetry) {
    return new ExtensionSecretEntries(extensions, extensionSecrets, connectionLock, telemetry);
  }

  @Bean
  ConnectionDeleteHooks connectionDeleteHooks(
      ExtensionConnections extensionConnections,
      ConnectionExtensionRegistry extensions,
      ExtensionContexts contexts,
      ExtensionSecretEntries extensionSecretEntries,
      DeleteHookTelemetry telemetry,
      DeleteHookRunner deleteHookRunner,
      ConnectionExtensionProperties properties) {
    return new ConnectionDeleteHooks(
        extensionConnections,
        extensions,
        contexts,
        extensionSecretEntries,
        telemetry,
        deleteHookRunner,
        properties.getDeleteHookTimeout());
  }
}
