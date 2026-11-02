// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration;

import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_BEHAVIOR;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_EXTENSION;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_INPUT;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SCHEMA;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.CONNECTION_SETTINGS;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.EGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.GEOCODING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INGRESS_MAPPING;
import static com.opencop.core.sdk.business.model.integration.capability.IntegrationCapability.INTEGRATION_IDENTITY;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EgressMapperAdmissionConfigurationTest {

  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");
  private static final UUID VALID_ID = UUID.fromString("55c68f5f-b176-454e-9010-4db517e80d2f");
  private static final UUID BLANK_NAME_ID = UUID.fromString("9b416055-46d7-4bd1-9041-0417427f3be7");

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(IntegrationCapabilityInfrastructureConfiguration.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void captureAdmissionLogs() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopCapturingAdmissionLogs() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void anInvalidEgressContributionIsLoggedAndSkippedWithoutFailingStartup() {
    new ApplicationContextRunner()
        .withUserConfiguration(AdmissionConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var contributions = context.getBean(EgressMapperContributions.class);
              assertThat(contributions.find(VALID_ID)).isPresent();
              assertThat(contributions.find(BLANK_NAME_ID)).isEmpty();
              assertThat(appender.list)
                  .extracting(ILoggingEvent::getFormattedMessage)
                  .singleElement()
                  .asString()
                  .contains(
                      "Rejected egress mapper contribution",
                      "reason=blank-name",
                      "mapperId=" + BLANK_NAME_ID);
            });
  }

  private record TestMapper(UUID id, String name, int version) implements PayloadEgressMapper {

    @Override
    public String description() {
      return "test contribution";
    }

    @Override
    public byte[] map(Feature feature) {
      return new byte[0];
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = EGRESS_MAPPING,
      rejected = {
        INGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_INPUT,
        GEOCODING,
        CONNECTION_EXTENSION
      })
  static class AdmissionConfiguration {

    @Bean
    IntegrationEgressMappers testEgressMappers() {
      return new IntegrationEgressMappers(
          INTEGRATION,
          List.of(
              new TestMapper(VALID_ID, "Valid egress", 1), new TestMapper(BLANK_NAME_ID, " ", 1)));
    }
  }
}
