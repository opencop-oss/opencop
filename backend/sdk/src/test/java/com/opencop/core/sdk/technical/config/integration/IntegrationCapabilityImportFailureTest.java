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

import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import com.opencop.core.sdk.technical.config.FrameworkCompatibleConfigurationBeanNameGenerator;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

class IntegrationCapabilityImportFailureTest {

  @TempDir Path temporaryDirectory;

  @Test
  void ignoresRejectedIndexesButLeavesSupportedImportFailuresToSpring() throws Exception {
    writeIndex("integration-identity", "missing.RejectedConfiguration");
    try (var rejectedIndexClassLoader = classLoader()) {
      capabilityContext(rejectedIndexClassLoader)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeansOfType(IngressMappingContribution.class)).hasSize(1);
              });
    }

    writeIndex("ingress-mapping", "missing.SupportedConfiguration");
    try (var supportedIndexClassLoader = classLoader()) {
      capabilityContext(supportedIndexClassLoader)
          .run(
              context -> {
                assertThat(context).hasFailed();
                assertThat(causeMessages(context.getStartupFailure()))
                    .contains("missing/SupportedConfiguration");
              });
    }
  }

  private ApplicationContextRunner capabilityContext(ClassLoader classLoader) {
    return new ApplicationContextRunner(
            () -> {
              var context = new AnnotationConfigApplicationContext();
              context.setBeanNameGenerator(
                  FrameworkCompatibleConfigurationBeanNameGenerator.INSTANCE);
              return context;
            })
        .withClassLoader(classLoader)
        .withUserConfiguration(IngressOnlyPolicy.class);
  }

  private URLClassLoader classLoader() throws Exception {
    return new URLClassLoader(
        new URL[] {temporaryDirectory.toUri().toURL()},
        IntegrationCapabilityImportFailureTest.class.getClassLoader());
  }

  private void writeIndex(String stableKey, String content) throws Exception {
    var resource =
        temporaryDirectory.resolve(
            "META-INF/opencop/integration-capabilities/v1/" + stableKey + ".imports");
    Files.createDirectories(resource.getParent());
    Files.writeString(resource, content, StandardCharsets.UTF_8);
  }

  private static String causeMessages(Throwable failure) {
    var messages = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      messages.append(cause.getMessage()).append('\n');
    }
    return messages.toString();
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
  static class IngressOnlyPolicy {}
}
