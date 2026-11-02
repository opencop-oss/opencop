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

import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IntegrationCapabilityBeanNamingTest {

  @Test
  void fullyQualifiedUnnamedBeansCoexistWhileDuplicateExplicitNamesFail() {
    capabilityContext()
        .withUserConfiguration(RejectAllPolicy.class, FirstUnnamed.class, SecondUnnamed.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(Helper.class)).hasSize(2);
              assertThat(context)
                  .hasBean(FirstUnnamed.class.getName() + ".helper")
                  .hasBean(SecondUnnamed.class.getName() + ".helper");
            });

    capabilityContext()
        .withUserConfiguration(RejectAllPolicy.class, FirstExplicit.class, SecondExplicit.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanDefinitionOverrideException.class);
            });
  }

  private static ApplicationContextRunner capabilityContext() {
    return new ApplicationContextRunner(
            () -> {
              var context = new AnnotationConfigApplicationContext();
              context.setBeanNameGenerator(
                  FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
              return context;
            })
        .withAllowBeanDefinitionOverriding(false);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableIntegrationCapabilities(
      supported = {},
      rejected = {
        EGRESS_MAPPING,
        CONNECTION_MAPPING,
        INTEGRATION_IDENTITY,
        CONNECTION_SCHEMA,
        INGRESS_MAPPING,
        CONNECTION_SETTINGS,
        CONNECTION_BEHAVIOR,
        CONNECTION_INPUT,
        GEOCODING,
        CONNECTION_EXTENSION
      })
  static class RejectAllPolicy {}

  @Configuration(proxyBeanMethods = false)
  static class FirstUnnamed {

    @Bean
    Helper helper() {
      return new Helper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SecondUnnamed {

    @Bean
    Helper helper() {
      return new Helper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class FirstExplicit {

    @Bean("duplicateExplicit")
    Helper firstHelper() {
      return new Helper();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class SecondExplicit {

    @Bean("duplicateExplicit")
    Helper secondHelper() {
      return new Helper();
    }
  }

  static final class Helper {}
}
