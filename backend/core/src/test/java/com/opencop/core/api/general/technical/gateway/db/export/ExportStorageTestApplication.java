// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import com.opencop.core.api.component.support.CoreComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
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
  JpaExportArtifactRepository.class,
  JpaActiveFeatureExportGateway.class,
  CoreComponentTestDatabaseConfig.class
})
public class ExportStorageTestApplication {}
