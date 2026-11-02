// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.source;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.component.support.MockAuditRecordingConfig;
import com.opencop.core.api.general.business.service.ConnectRoutingTargetResolver;
import com.opencop.core.api.general.business.service.DataSourceAutoStartService;
import com.opencop.core.api.general.technical.config.CoreProperties;
import com.opencop.core.api.general.technical.endpoint.source.DataSourceAutoStartEndpoint;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EnableConfigurationProperties(CoreProperties.class)
@Import({
  ComponentTestDatabaseConfig.class,
  MockAuditRecordingConfig.class,
  ConnectRoutingTargetResolver.class,
  CoreComponentWebTestConfig.class,
  DataSourceAutoStartEndpoint.class,
  DataSourceAutoStartService.class,
  JdbcDataSourceRepository.class
})
public class DataSourceAutoStartWebTestApplication {}
