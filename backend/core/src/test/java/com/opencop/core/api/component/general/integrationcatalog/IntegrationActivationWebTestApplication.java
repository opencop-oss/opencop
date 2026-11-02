// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.config.IntegrationActivationConfiguration;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationActivationEndpoint;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationActivationExceptionAdvice;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.api.general.technical.gateway.db.connection.JpaConnectionIntegrationSettingsGateway;
import com.opencop.core.api.general.technical.gateway.db.connection.JpaConnectionMetadataGateway;
import com.opencop.core.api.general.technical.gateway.db.connection.PatchableDataSourceEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.JpaIntegrationCatalogGateway;
import com.opencop.core.api.general.technical.gateway.db.profile.MapperIdentityEntity;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(
    basePackageClasses = {
      IntegrationConnectionSchemaEntity.class,
      MapperIdentityEntity.class,
      PatchableDataSourceEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      IntegrationConnectionSchemaJpaRepository.class,
      JpaConnectionIntegrationSettingsGateway.class
    })
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  IntegrationActivationConfiguration.class,
  IntegrationActivationEndpoint.class,
  IntegrationActivationExceptionAdvice.class,
  JdbcDataSourceRepository.class,
  JpaConnectionIntegrationSettingsGateway.class,
  JpaConnectionMetadataGateway.class,
  JpaIntegrationCatalogGateway.class
})
class IntegrationActivationWebTestApplication {}
