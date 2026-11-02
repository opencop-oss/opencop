// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.userdirectory;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.endpoint.userdirectory.UserDirectoryEndpoint;
import com.opencop.core.api.general.technical.gateway.db.userdirectory.UserDirectoryEntryEntity;
import com.opencop.core.api.general.technical.gateway.db.userdirectory.UserDirectoryEntryRepository;
import com.opencop.core.api.general.technical.gateway.db.userdirectory.UserDirectoryQueryService;
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
@EntityScan(basePackageClasses = UserDirectoryEntryEntity.class)
@EnableJpaRepositories(basePackageClasses = UserDirectoryEntryRepository.class)
@Import({
  UserDirectoryEndpoint.class,
  UserDirectoryQueryService.class,
  CoreComponentWebTestConfig.class,
  ComponentTestDatabaseConfig.class
})
public class UserDirectoryWebTestApplication {}
