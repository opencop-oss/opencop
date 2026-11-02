// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.JpaIntegrationCatalogGateway;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = IntegrationConnectionSchemaEntity.class)
@EnableJpaRepositories(basePackageClasses = IntegrationConnectionSchemaJpaRepository.class)
@Import({JpaIntegrationCatalogGateway.class, CoreComponentTestDatabaseConfig.class})
class IntegrationCatalogTestApplication {}
