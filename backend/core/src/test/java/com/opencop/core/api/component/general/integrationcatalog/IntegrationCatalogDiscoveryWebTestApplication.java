// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.config.IntegrationCatalogDiscoveryConfiguration;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationCatalogEndpoint;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationCatalogExceptionAdvice;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.JpaIntegrationCatalogGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Loads provider-neutral connection schemas covering each axis the listing sorts and filters on: a
 * TCP integration with two versions, a UDP one, and one declaring both transports. Catalog rows the
 * test seeds without a matching bean here are the unavailable case.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = IntegrationConnectionSchemaEntity.class)
@EnableJpaRepositories(basePackageClasses = IntegrationConnectionSchemaJpaRepository.class)
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  IntegrationCatalogDiscoveryConfiguration.class,
  IntegrationCatalogEndpoint.class,
  IntegrationCatalogExceptionAdvice.class,
  JpaIntegrationCatalogGateway.class
})
class IntegrationCatalogDiscoveryWebTestApplication {

  static final UUID TCP_ID = UUID.fromString("8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20");
  static final UUID UDP_ID = UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31");
  static final UUID DUAL_ID = UUID.fromString("7c3d2e6a-4f5b-4c7d-9e80-5a6b7c8d9e42");

  /** Seeded by the test but backed by no loaded schema, so this runtime cannot serve it. */
  static final UUID UNLOADED_ID = UUID.fromString("6d4e3f7b-5a6c-4d8e-8f91-4b5c6d7e8f53");

  /** Seeded by the test with no connection schema version at all. */
  static final UUID MAPPER_ONLY_ID = UUID.fromString("5e5f4a8c-6b7d-4e9f-9a02-3c4d5e6f7a64");

  static final String TCP_NAME = "Streaming feed";
  static final String UDP_NAME = "Datagram feed";
  static final String DUAL_NAME = "Dual stack feed";
  static final String UNLOADED_NAME = "Retired feed";
  static final String MAPPER_ONLY_NAME = "Mapper only";

  @Bean
  IntegrationConnectionSchema tcpSchemaVersionOne() {
    return new TestConnectionSchema(
        TCP_ID, 1, TCP_NAME, "Streams over a persistent socket.", Set.of(TransportProtocol.TCP));
  }

  @Bean
  IntegrationConnectionSchema tcpSchemaVersionTwo() {
    return new TestConnectionSchema(
        TCP_ID, 2, TCP_NAME, "Streams over a persistent socket.", Set.of(TransportProtocol.TCP));
  }

  @Bean
  IntegrationConnectionSchema udpSchema() {
    return new TestConnectionSchema(
        UDP_ID, 1, UDP_NAME, "Receives datagrams.", Set.of(TransportProtocol.UDP));
  }

  @Bean
  IntegrationConnectionSchema dualStackSchema() {
    return new TestConnectionSchema(
        DUAL_ID,
        1,
        DUAL_NAME,
        "Serves either transport.",
        Set.of(TransportProtocol.TCP, TransportProtocol.UDP));
  }
}
