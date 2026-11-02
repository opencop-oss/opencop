// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.core.io.ClassPathResource;

class IdpRelationshipPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(TestConfig.class);

  @Test
  void explicitUserIdClaimBinds() {
    contextRunner
        .withPropertyValues(
            "idp.user-id-claim=sub",
            "idp.audience=CORE-API",
            "idp.allowed.0.group=group-a",
            "idp.allowed.0.roles=operator,auditor")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(IdpRelationshipProperties.class);
              assertThat(properties.getUserIdClaim()).isEqualTo("sub");
              assertThat(properties.getAudience()).isEqualTo("CORE-API");
              assertThat(properties.getRelationshipConfigs()).hasSize(1);
              assertThat(properties.getRelationshipConfigs().getFirst().getGroup())
                  .isEqualTo("group-a");
              assertThat(properties.getRelationshipConfigs().getFirst().getRoles())
                  .containsExactly("operator", "auditor");
            });
  }

  @Test
  void missingUserIdClaimFailsStartup() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void blankUserIdClaimFailsStartup() {
    contextRunner
        .withPropertyValues("idp.user-id-claim=   ")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void defaultProductionConfigurationDoesNotSupplyAnIdentityClaimFallback() throws IOException {
    var defaultConfiguration =
        new ClassPathResource("application.yml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

    assertThat(defaultConfiguration).doesNotContain("user-id-claim");
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IdpRelationshipProperties.class)
  static class TestConfig {}
}
