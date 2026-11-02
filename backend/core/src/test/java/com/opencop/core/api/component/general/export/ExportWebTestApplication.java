// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.export;

import static org.mockito.Mockito.mock;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.component.support.MockAuditRecordingConfig;
import com.opencop.core.api.general.business.export.CsvFeatureExportMapper;
import com.opencop.core.api.general.business.export.ExportFilenameFactory;
import com.opencop.core.api.general.business.export.ExportFormatResolver;
import com.opencop.core.api.general.business.policy.ExportAccessPolicy;
import com.opencop.core.api.general.business.policy.ResourceLifecycleGuard;
import com.opencop.core.api.general.business.usecase.export.DownloadExportUsecase;
import com.opencop.core.api.general.business.usecase.export.ExportFeaturesUsecase;
import com.opencop.core.api.general.business.usecase.export.strategy.ElementIdsExportStrategy;
import com.opencop.core.api.general.business.usecase.export.strategy.ExportSelectionStrategies;
import com.opencop.core.api.general.business.usecase.export.strategy.SubjectsExportStrategy;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.endpoint.export.ExportsEndpoint;
import com.opencop.core.api.general.technical.gateway.blob.export.BlobExportDownloadUrlGateway;
import com.opencop.core.api.general.technical.gateway.db.export.ActiveFeatureExportJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.export.ExportArtifactEntity;
import com.opencop.core.api.general.technical.gateway.db.export.ExportArtifactJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.export.GeoFeatureExportEntity;
import com.opencop.core.api.general.technical.gateway.db.export.JpaActiveFeatureExportGateway;
import com.opencop.core.api.general.technical.gateway.db.export.JpaExportArtifactRepository;
import com.opencop.core.api.iam.ports.usecase.access.layer.LayerManager;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.business.model.iam.access.LayerMember;
import com.opencop.core.sdk.ports.gateway.blob.BlobClient;
import com.opencop.core.sdk.technical.gateway.blob.azure.AzureProperties;
import java.time.Clock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {ExportArtifactEntity.class, GeoFeatureExportEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {
      ExportArtifactJpaRepository.class,
      ActiveFeatureExportJpaRepository.class
    })
@Import({
  ExportsEndpoint.class,
  MockAuditRecordingConfig.class,
  ExportFeaturesUsecase.class,
  ExportSelectionStrategies.class,
  SubjectsExportStrategy.class,
  ElementIdsExportStrategy.class,
  DownloadExportUsecase.class,
  ExportAccessPolicy.class,
  ExportFormatResolver.class,
  CsvFeatureExportMapper.class,
  ExportFilenameFactory.class,
  BlobExportDownloadUrlGateway.class,
  JpaActiveFeatureExportGateway.class,
  JpaExportArtifactRepository.class,
  CoreComponentWebTestConfig.class,
  CoreComponentTestDatabaseConfig.class
})
public class ExportWebTestApplication {

  @Bean
  ResourceLifecycleGuard resourceLifecycleGuard() {
    return mock(ResourceLifecycleGuard.class);
  }

  @Bean
  UserSpecificManager userSpecificManager() {
    return mock(UserSpecificManager.class);
  }

  @Bean
  LayerManager<Layer, LayerMember> layerManager() {
    return mock(LayerManager.class);
  }

  @Bean
  DataSourceRepository dataSourceRepository() {
    return mock(DataSourceRepository.class);
  }

  @Bean
  BlobClient blobClient() {
    return mock(BlobClient.class);
  }

  @Bean
  BlobServiceClient blobServiceClient() {
    return mock(BlobServiceClient.class);
  }

  @Bean
  BlobContainerClient blobContainerClient() {
    return mock(BlobContainerClient.class);
  }

  @Bean
  com.azure.storage.blob.BlobClient azureBlobClient() {
    return mock(com.azure.storage.blob.BlobClient.class);
  }

  @Bean
  @Primary
  AzureProperties azureProperties() {
    var properties = new AzureProperties();
    properties.setContainerName("opencop-common");
    properties.setEndpoint("http://azurite:10000/devstoreaccount1");
    properties.setPublicEndpoint("http://localhost:10000/devstoreaccount1");
    return properties;
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
