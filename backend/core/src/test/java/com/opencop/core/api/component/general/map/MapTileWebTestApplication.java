// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.map;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.business.usecase.map.MapTileGrantUsecase;
import com.opencop.core.api.general.business.usecase.map.MapTileManagementUsecase;
import com.opencop.core.api.general.technical.endpoint.map.MapTileEndpoint;
import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileGrantService;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
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
@EntityScan(basePackageClasses = MapTileEntity.class)
@EnableJpaRepositories(basePackageClasses = MapTileRepository.class)
@Import({
  MapTileManagementUsecase.class,
  MapTileEndpoint.class,
  MapTileService.class,
  EffectiveMapTileAccessService.class,
  MapTileGrantService.class,
  MapTileAccessUsecase.class,
  MapTileGrantUsecase.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class MapTileWebTestApplication {}
