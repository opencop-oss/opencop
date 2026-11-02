// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.business.usecase.map.MapTileGrantUsecase;
import com.opencop.core.api.general.business.usecase.map.MapTileManagementUsecase;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.business.usecase.user.RetrieveMeUsecase;
import com.opencop.core.api.general.technical.endpoint.map.MapTileEndpoint;
import com.opencop.core.api.general.technical.endpoint.user.MeEndpoint;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceEntity;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileGrantService;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.session.RedisSessionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(
    basePackageClasses = {
      MapTileEntity.class,
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class,
      UserPreferenceEntity.class,
      CurrentAccountAccessEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      MapTileRepository.class,
      InternalUserRepository.class,
      CurrentAccountAccessRepository.class
    })
@Import({
  // The tile HTTP stack rides along so MapTileSecurityIT can exercise these routes against the
  // production filter chain rather than the permissive component-test one.
  MapTileEndpoint.class,
  MapTileManagementUsecase.class,
  MapTileService.class,
  MapTileGrantService.class,
  MapTileAccessUsecase.class,
  MapTileGrantUsecase.class,
  EffectiveMapTileAccessService.class,
  ComponentTestDatabaseConfig.class,
  RedisSessionConfig.class,
  SecurityConfig.class,
  CurrentAccountAccessFilter.class,
  CurrentAccountAuthenticationFactory.class,
  CurrentAccountAccessFactory.class,
  CurrentAccountAccessPolicy.class,
  ResolveCurrentAccountAccessUsecase.class,
  PostgresCurrentAccountAccessGateway.class,
  RedisCurrentAccountAccessGateway.class,
  CurrentAccountCacheTelemetry.class,
  CurrentAccountSessionTelemetry.class,
  InternalUserService.class,
  MeEndpoint.class,
  RetrieveMeUsecase.class
})
public class OidcAdmissionTestApplication {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }
}
