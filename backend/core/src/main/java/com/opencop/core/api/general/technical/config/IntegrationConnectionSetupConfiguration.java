// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.integrationcatalog.DescribeIntegrationConnectionSetupUsecase;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescribers;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.kafka.KafkaConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.rtsp.RtspConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.tcp.TcpConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.udp.UdpConnectionSetupDescriber;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.integrationcatalog.DescribeIntegrationConnectionSetup;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationConnectionSetupConfiguration {

  @Bean
  DescribeIntegrationConnectionSetup describeIntegrationConnectionSetup(
      IntegrationCatalogGateway catalogGateway,
      ConnectionSetupDescribers connectionSetupDescribers,
      List<IntegrationConnectionSchema> schemas) {
    return new DescribeIntegrationConnectionSetupUsecase(
        catalogGateway, connectionSetupDescribers, schemas);
  }

  @Bean
  ConnectionSetupDescribers connectionSetupDescribers(List<ConnectionSetupDescriber> describers) {
    return new ConnectionSetupDescribers(describers);
  }

  @Bean
  ConnectionSetupDescriber tcpConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    return new TcpConnectionSetupDescriber(extractorRegistry, mappingRegistry);
  }

  @Bean
  ConnectionSetupDescriber udpConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    return new UdpConnectionSetupDescriber(extractorRegistry, mappingRegistry);
  }

  @Bean
  ConnectionSetupDescriber rtspConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    return new RtspConnectionSetupDescriber(extractorRegistry, mappingRegistry);
  }

  @Bean
  ConnectionSetupDescriber kafkaConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    return new KafkaConnectionSetupDescriber(extractorRegistry, mappingRegistry);
  }
}
