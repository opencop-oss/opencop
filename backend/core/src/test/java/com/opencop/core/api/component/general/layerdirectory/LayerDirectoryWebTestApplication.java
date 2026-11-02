// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layerdirectory;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.endpoint.layer.LayerDirectoryEndpoint;
import com.opencop.core.api.iam.technical.gateway.db.layers.directory.LayerDirectoryEntity;
import com.opencop.core.api.iam.technical.gateway.db.layers.directory.LayerDirectoryEntryRepository;
import com.opencop.core.api.iam.technical.gateway.db.layers.directory.LayerDirectoryQueryService;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// LayerDirectoryEntity is a self-contained @Subselect (no associations), so scanning just its
// package is enough — the underlying iam.layer / iam.layer_group rows are seeded via JdbcTemplate.
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = LayerDirectoryEntity.class)
@EnableJpaRepositories(basePackageClasses = LayerDirectoryEntryRepository.class)
@Import({
  LayerDirectoryEndpoint.class,
  LayerDirectoryQueryService.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class LayerDirectoryWebTestApplication {}
