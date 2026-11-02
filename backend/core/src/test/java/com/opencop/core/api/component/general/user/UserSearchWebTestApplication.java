// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import static org.mockito.Mockito.mock;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.api.general.business.usecase.user.AccountMutationUsecase;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.endpoint.user.UsersEndpoint;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.iam.business.usecase.identity.UserManagersFactory;
import com.opencop.core.api.iam.ports.usecase.identity.UserManager;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// InternalUserEntity has a @ManyToMany to InternalGroupEntity (and onward to the relationship
// entity), so the whole scim entity graph must be in the persistence unit — scan the package.
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.opencop.core.api.general.technical.gateway.scim")
@EnableJpaRepositories(basePackageClasses = InternalUserRepository.class)
@Import({
  UsersEndpoint.class,
  InternalUserService.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class UserSearchWebTestApplication {

  @Bean
  UserManager<InternalUser> userManager(
      UserService userService, AccountMutationUsecase accountMutationUsecase) {
    return UserManagersFactory.userManager(userService, accountMutationUsecase);
  }

  // The search endpoint never mutates admin state, so a stub satisfies the manager's dependency.
  @Bean
  AccountMutationUsecase accountMutationUsecase() {
    return mock(AccountMutationUsecase.class);
  }
}
