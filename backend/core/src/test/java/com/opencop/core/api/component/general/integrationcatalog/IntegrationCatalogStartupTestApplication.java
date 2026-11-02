// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.general.technical.config.IntegrationCatalogConfiguration;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.JpaIntegrationCatalogGateway;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableIntegrationCapabilities(
    supported = {INTEGRATION_IDENTITY, CONNECTION_SCHEMA},
    rejected = {
      EGRESS_MAPPING,
      INGRESS_MAPPING,
      CONNECTION_SETTINGS,
      CONNECTION_BEHAVIOR,
      CONNECTION_INPUT,
      CONNECTION_MAPPING,
      GEOCODING,
      CONNECTION_EXTENSION
    })
@EntityScan(basePackageClasses = IntegrationConnectionSchemaEntity.class)
@EnableJpaRepositories(basePackageClasses = IntegrationConnectionSchemaJpaRepository.class)
@Import({
  JpaIntegrationCatalogGateway.class,
  IntegrationCatalogConfiguration.class,
  CoreComponentTestDatabaseConfig.class
})
class IntegrationCatalogStartupTestApplication {}
