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
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.policy.integration.mapping.ingress.IngressMapperContributionPolicy;
import com.opencop.core.sdk.business.usecase.integration.mapping.ingress.IngressMapperAdmissionUsecase;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
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

class IngressMapperAdmissionConfigurationTest {

  private static final UUID VALID_ID = UUID.fromString("55c68f5f-b176-454e-9010-4db517e80d2e");
  private static final UUID DUPLICATE_ID = UUID.fromString("8809bb83-929e-4cac-b127-73fc91ead8c0");
  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");
  private static final UUID BLANK_NAME_ID = UUID.fromString("9b416055-46d7-4bd1-9041-0417427f3be6");

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
  void exposesOnlyAdmittedContributionsToConsumers() {
    new ApplicationContextRunner()
        .withUserConfiguration(AdmissionConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(IngressMapperContributionPolicy.class);
              assertThat(context).hasSingleBean(IngressMapperAdmissionUsecase.class);
              assertThat(context.getBean(ContributionProbe.class).contributions().mappers())
                  .extracting(PayloadIngressMapper::id)
                  .contains(VALID_ID)
                  .doesNotContain(BLANK_NAME_ID, DUPLICATE_ID);
              assertThat(rejectionMessages())
                  .hasSize(3)
                  .anySatisfy(
                      message ->
                          assertThat(message)
                              .contains("reason=blank-name", "mapperId=" + BLANK_NAME_ID))
                  .filteredOn(message -> message.contains("reason=duplicate-uuid"))
                  .hasSize(2)
                  .allSatisfy(message -> assertThat(message).contains("mapperId=" + DUPLICATE_ID));
            });
  }

  @Test
  void leavesDecoderConstructionFailureAsSpringStartupFailure() {
    new ApplicationContextRunner()
        .withUserConfiguration(ConstructionFailureConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(causeMessages(context.getStartupFailure()))
                  .contains("mapper construction failed");
            });
  }

  private static String causeMessages(Throwable failure) {
    var messages = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      messages.append(cause.getMessage()).append('\n');
    }
    return messages.toString();
  }

  private List<String> rejectionMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private static PayloadIngressMapper<Feature> decoder(UUID id, String name, int version) {
    return new TestDecoder(id, name, version);
  }

  private static IntegrationIngressMappers group(PayloadIngressMapper<Feature> decoder) {
    return new IntegrationIngressMappers(INTEGRATION, List.of(decoder));
  }

  private record ContributionProbe(IngressMapperContributions contributions) {}

  private record TestDecoder(UUID id, String name, int version)
      implements PayloadIngressMapper<Feature> {

    @Override
    public String description() {
      return "test contribution";
    }

    @Override
    public List<Feature> map(UUID objectId, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {
        EGRESS_MAPPING,
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
    IntegrationIngressMappers validMapper() {
      return group(decoder(VALID_ID, "Valid mapper", 1));
    }

    @Bean
    IntegrationIngressMappers blankNameMapper() {
      return group(decoder(BLANK_NAME_ID, " ", 1));
    }

    @Bean
    IntegrationIngressMappers firstDuplicateMapper() {
      return group(decoder(DUPLICATE_ID, "First duplicate", 1));
    }

    @Bean
    IntegrationIngressMappers secondDuplicateMapper() {
      return group(decoder(DUPLICATE_ID, "Second duplicate", 2));
    }

    @Bean
    ContributionProbe contributionProbe(IngressMapperContributions contributions) {
      return new ContributionProbe(contributions);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = INGRESS_MAPPING,
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_INPUT,
        GEOCODING,
        CONNECTION_EXTENSION
      })
  static class ConstructionFailureConfiguration {

    @Bean
    IntegrationIngressMappers failingMapper() {
      throw new IllegalStateException("mapper construction failed");
    }
  }
}
