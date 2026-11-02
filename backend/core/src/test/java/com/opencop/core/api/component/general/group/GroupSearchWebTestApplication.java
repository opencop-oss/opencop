// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.group;

import static org.mockito.Mockito.mock;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.endpoint.group.GroupsEndpoint;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupLayerService;
import com.opencop.core.api.general.technical.gateway.db.group.AppGroupService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.member.LayerMemberEntity;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// The persistence unit has to reach past the group table because of two association chains.
// InternalUserEntity has a @ManyToMany to InternalGroupEntity and onward to the relationship
// entity, so the whole scim graph must be scanned. GroupLayerMemberEntity shares GroupEntity's
// package and targets LayerEntity, which in turn owns LayerMemberEntity.
@SpringBootConfiguration
@EnableAutoConfiguration
// Method security is off by default in a slice. Without it the @PreAuthorize on the admin listing
// is inert, so the admin-only route would wrongly answer 200 and prove nothing.
@EnableMethodSecurity
@EntityScan(
    basePackages = "com.opencop.core.api.general.technical.gateway.scim",
    basePackageClasses = {GroupEntity.class, LayerEntity.class, LayerMemberEntity.class})
@EnableJpaRepositories(
    basePackageClasses = {
      IamGroupRepository.class,
      IamGroupMembersRepository.class,
      InternalUserRepository.class
    })
@Import({
  GroupsEndpoint.class,
  AppGroupService.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class GroupSearchWebTestApplication {

  // The search route never touches layer assignment, so a stub satisfies the endpoint's dependency.
  @Bean
  AppGroupLayerService appGroupLayerService() {
    return mock(AppGroupLayerService.class);
  }
}
