// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;

import com.opencop.core.api.common.filter.RequestLoggingFilter;
import com.opencop.core.api.general.technical.config.IdpRelationshipProperties;
import com.opencop.core.api.general.technical.config.IntegrationCatalogConfiguration;
import com.opencop.core.api.general.technical.config.PluginMapperRegistrationConfiguration;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry;
import com.opencop.core.sdk.technical.audit.AuthzAuditConfig;
import com.opencop.core.sdk.technical.config.integration.EnableIntegrationCapabilities;
import com.opencop.core.sdk.technical.config.integration.connection.extension.ConnectionExtensionRegistryConfiguration;
import com.opencop.core.sdk.technical.config.integration.connection.input.ConnectionExtractorConfiguration;
import com.opencop.core.sdk.technical.config.integration.connection.mapping.ConnectionMappingConfiguration;
import com.opencop.core.sdk.technical.config.integration.geocoding.GeocoderRegistryConfiguration;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// Core hosts ingress mapping, connection-input and geocoding workflows, and validates and reports
// connection settings, but intentionally rejects connection behavior. Connection schemas, and the
// integration identity they belong to, activate as prerequisites of connection input and mapping.
// This policy is exhaustive: every catalog addition must be classified here before Core can start.
@EnableIntegrationCapabilities(
    supported = {
      INGRESS_MAPPING,
      EGRESS_MAPPING,
      CONNECTION_INPUT,
      CONNECTION_SETTINGS,
      CONNECTION_MAPPING,
      GEOCODING,
      CONNECTION_EXTENSION
    },
    rejected = CONNECTION_BEHAVIOR)
@ComponentScan({
  "com/opencop/core/sdk/technical/gateway/blob",
  "com/opencop/core/sdk/technical/exception",
  "com/opencop/core/sdk/technical/security/session",
  "com/opencop/core/sdk/technical/gateway/idp",
  "com/opencop/core/sdk/technical/gateway/profile",
  "com/opencop/core/sdk/technical/mapper",
  "com/opencop/core/sdk/technical/gateway/secrets",
  "com/opencop/core/sdk/technical/gateway/db/notification",
  "com/opencop/core/sdk/technical/gateway/broker",
  "com/opencop/core/sdk/technical/gateway/mapping",
  "com/opencop/core/sdk/technical/gateway/iam",
  "com/opencop/core/sdk/technical/gateway/db/account",
  "com/opencop/core/sdk/technical/gateway/db/media"
})
@EntityScan(basePackages = "com.opencop")
@EnableJpaRepositories(basePackages = "com.opencop")
@EnableAsync
@Import({
  CoreSecurityExceptionAdvice.class,
  AuthzAuditConfig.class,
  IntegrationCatalogConfiguration.class,
  PluginMapperRegistrationConfiguration.class,
  ConnectionExtractorConfiguration.class,
  ConnectionMappingConfiguration.class,
  GeocoderRegistryConfiguration.class,
  ConnectionExtensionRegistryConfiguration.class
})
@EnableConfigurationProperties(IdpRelationshipProperties.class)
@EnableMethodSecurity // For @PreAuthorize("@authz.isAdmin(authentication)")
public class AppConfig {

  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public CurrentAccountAccessPolicy currentAccountAccessPolicy() {
    return new CurrentAccountAccessPolicy();
  }

  @Bean
  public Tracer historicalSubmissionTracer() {
    return GlobalOpenTelemetry.getTracer(HistoricalTelemetry.SUBMISSION_TRACER_NAME);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "opencop.scim",
      name = "inbound-logging-enabled",
      havingValue = "true")
  public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
    log.warn(
        "SCIM inbound diagnostics are enabled. Authenticated SCIM identity payloads may be written to application logs; do not enable for routine production use.");
    FilterRegistrationBean<RequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new RequestLoggingFilter());
    registrationBean.addUrlPatterns("/scim/*");
    return registrationBean;
  }
}
