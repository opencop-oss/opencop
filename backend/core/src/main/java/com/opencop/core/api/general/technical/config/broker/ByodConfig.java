// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config.broker;

import com.opencop.core.api.general.business.service.AmbiguousPersistenceOutcomeHandler;
import com.opencop.core.api.general.business.service.ByodReconciliationService;
import com.opencop.core.api.general.business.service.KafkaConnectionPersistenceService;
import com.opencop.core.api.general.business.service.SelectedMapperLock;
import com.opencop.core.api.general.business.usecase.connection.DataSourceAuditProjection;
import com.opencop.core.api.general.business.usecase.connection.persistence.KafkaConnectionPersistenceHelper;
import com.opencop.core.api.general.ports.gateway.audit.AuditRecorder;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.kafka.KafkaProvisioningService;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.connection.permissions.CreateSourcePermissions;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.api.general.technical.gateway.kafka.ByodSourcePublisher;
import com.opencop.core.sdk.model.datasource.ByodSourceState;
import com.opencop.core.sdk.technical.gateway.broker.kafka.config.BaseKafkaConfig;
import com.opencop.core.sdk.technical.gateway.broker.kafka.properties.ProviderConfig;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

@Configuration(proxyBeanMethods = false)
public class ByodConfig {

  private static final Logger log = LoggerFactory.getLogger(ByodConfig.class);

  @Bean
  KafkaConnectionPersistenceHelper kafkaConnectionPersistenceHelper(
      KafkaProvisioningService kafkaProvisioningService,
      KafkaConnectionPersistenceService persistenceService,
      AmbiguousPersistenceOutcomeHandler ambiguousOutcomeHandler,
      ByodSourcePublisher byodSourcePublisher,
      ProviderConfig providerConfig) {
    return new KafkaConnectionPersistenceHelper(
        kafkaProvisioningService,
        persistenceService,
        ambiguousOutcomeHandler,
        byodSourcePublisher,
        providerConfig.name());
  }

  @Bean
  KafkaConnectionPersistenceService kafkaConnectionPersistenceService(
      DataSourceRepository dataSourceRepository,
      CreateSourcePermissions createSourcePermissions,
      IntegrationCatalogGateway integrationCatalogGateway,
      SelectedMapperLock selectedMapperLock,
      AuditRecorder auditRecorder,
      DataSourceAuditProjection projection) {
    return new KafkaConnectionPersistenceService(
        dataSourceRepository,
        createSourcePermissions,
        integrationCatalogGateway,
        selectedMapperLock,
        auditRecorder,
        projection);
  }

  @Bean
  ByodSourcePublisher byodSourcePublisher(
      @Value("${opencop.byod.sources-topic}") String sourcesTopic,
      BaseKafkaConfig baseKafkaConfig) {
    return new ByodSourcePublisher(byodSourceKafkaTemplate(baseKafkaConfig), sourcesTopic);
  }

  @Bean
  ByodReconciliationService byodReconciliationService(
      DataSourceRepository dataSourceRepository,
      ByodSourcePublisher byodSourcePublisher,
      KafkaProvisioningService kafkaProvisioningService,
      ProviderConfig providerConfig) {
    return new ByodReconciliationService(
        dataSourceRepository, byodSourcePublisher, kafkaProvisioningService, providerConfig);
  }

  @Bean
  ApplicationRunner byodReconciliationRunner(
      ByodReconciliationService byodReconciliationService, StartupLeader startupLeader) {
    return _ -> {
      if (!startupLeader.isLeader()) {
        log.info("Not startup leader, skipping BYOD reconciliation");
        return;
      }
      byodReconciliationService.reconcile();
    };
  }

  private KafkaTemplate<String, ByodSourceState> byodSourceKafkaTemplate(
      BaseKafkaConfig baseKafkaConfig) {
    var props =
        new HashMap<>(
            Map.<String, Object>of(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class));
    props.putAll(baseKafkaConfig.props());
    return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
  }
}
