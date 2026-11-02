// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.map;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureEntity;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureRepository;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = MapCaptureEntity.class)
@EnableJpaRepositories(basePackageClasses = MapCaptureJpaRepository.class)
@Import({
  MapCaptureEndpoint.class,
  MapCaptureRepository.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class MapWebTestApplication {}
