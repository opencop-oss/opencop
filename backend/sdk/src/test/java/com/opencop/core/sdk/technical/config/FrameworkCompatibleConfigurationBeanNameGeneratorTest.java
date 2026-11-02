// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.type.MethodMetadata;

class FrameworkCompatibleConfigurationBeanNameGeneratorTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "entityManagerFactory",
        "transactionManager",
        "flyway",
        "flywayInitializer",
        "mvcContentNegotiationManager"
      })
  void keepsRequiredConventionalNamesForUnnamedSpringBeanMethods(String methodName) {
    var beanMethod =
        beanMethod(
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaConfiguration", methodName);

    assertThat(
            FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE.deriveBeanName(
                beanMethod, null))
        .isEqualTo(methodName);
  }

  @Test
  void fullyQualifiesUnnamedApplicationAndProviderBeanMethods() {
    var applicationBeanMethod = beanMethod("com.opencop.ApplicationConfiguration", "helper");
    var providerBeanMethod = beanMethod("com.example.ProviderConfiguration", "helper");

    assertThat(
            FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE.deriveBeanName(
                applicationBeanMethod, null))
        .isEqualTo("com.opencop.ApplicationConfiguration.helper");
    assertThat(
            FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE.deriveBeanName(
                providerBeanMethod, null))
        .isEqualTo("com.example.ProviderConfiguration.helper");
  }

  @Test
  void preservesExplicitBeanNamesRegardlessOfOwner() {
    var springBeanMethod =
        beanMethod(
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaConfiguration",
            "entityManagerFactory");

    assertThat(
            FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE.deriveBeanName(
                springBeanMethod, "explicitEntityManagerFactory"))
        .isEqualTo("explicitEntityManagerFactory");
  }

  private static MethodMetadata beanMethod(String declaringClassName, String methodName) {
    var metadata = mock(MethodMetadata.class);
    when(metadata.getDeclaringClassName()).thenReturn(declaringClassName);
    when(metadata.getMethodName()).thenReturn(methodName);
    return metadata;
  }
}
