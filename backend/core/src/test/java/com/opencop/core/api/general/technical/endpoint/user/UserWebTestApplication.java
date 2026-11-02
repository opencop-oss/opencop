// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.user;

import static org.mockito.Mockito.mock;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.usecase.map.MapTileAccessUsecase;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.business.usecase.user.RetrieveMeUsecase;
import com.opencop.core.api.general.business.usecase.user.UserPreferenceUsecase;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.db.user.JpaUserPreferenceRepository;
import com.opencop.core.api.general.technical.gateway.db.user.PostgresAccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceEntity;
import com.opencop.core.api.general.technical.gateway.db.user.UserPreferenceJpaRepository;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.service.EffectiveMapTileAccessService;
import com.opencop.core.api.iam.business.usecase.identity.UserManagersFactory;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(
    basePackageClasses = {
      MapTileEntity.class,
      UserPreferenceEntity.class,
      InternalUserEntity.class,
      InternalGroupEntity.class,
      InternalUserGroupRelationshipEntity.class
    })
@EnableJpaRepositories(
    basePackageClasses = {
      MapTileRepository.class,
      UserPreferenceJpaRepository.class,
      InternalUserRepository.class
    })
@Import({
  MapTileAccessUsecase.class,
  UserPreferenceUsecase.class,
  EffectiveMapTileAccessService.class,
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  CsrfEndpoint.class,
  MeEndpoint.class,
  RetrieveMeUsecase.class,
  UserPreferencesEndpoint.class,
  UsersEndpoint.class,
  JpaUserPreferenceRepository.class,
  InternalUserService.class,
  AccountMutationPolicy.class,
  AccountMutationUsecase.class,
  PostgresAccountMutationGateway.class,
  CurrentAccountCacheTelemetry.class,
  RedisCurrentAccountAccessGateway.class
})
public class UserWebTestApplication {

  @Bean
  UserManager<InternalUser> userManager(
      UserService userService, AccountMutationUsecase accountMutationUsecase) {
    return UserManagersFactory.userManager(userService, accountMutationUsecase);
  }

  @Bean
  RetrieveMedia retrieveMedia() {
    return mock(RetrieveMedia.class);
  }
}
