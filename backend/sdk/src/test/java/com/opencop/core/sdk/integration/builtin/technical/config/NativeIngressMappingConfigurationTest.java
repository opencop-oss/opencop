// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.GeoJsonIngressMapperV1;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.KlvIngressMapperV1;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NativeIngressMappingConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(NativeIngressMappingConfiguration.class);

  @Test
  void groupsTheGeoJsonAndKlvMappersUnderTheNativeIntegration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(IntegrationIngressMappers.class);
          assertThat(context).doesNotHaveBean(PayloadIngressMapper.class);
          var group = context.getBean(IntegrationIngressMappers.class);

          assertThat(group.integration()).isEqualTo(new NativeIntegration());
          assertThat(group.mappers())
              .extracting(PayloadIngressMapper::id)
              .containsExactly(GeoJsonIngressMapperV1.MAPPER_ID, KlvIngressMapperV1.MAPPER_ID);
          assertThat(group.mappers())
              .extracting(PayloadIngressMapper::name)
              .containsExactly("GEOJSON-FEATURE", KlvIngressMapperV1.MAPPER_NAME);
          assertThat(group.mappers()).extracting(PayloadIngressMapper::version).containsOnly(1);
          assertThat(group.mappers())
              .extracting(PayloadIngressMapper::objectIdStability)
              .containsExactly(ObjectIdStability.UNKNOWN, ObjectIdStability.STABLE_PER_ENTITY);
        });
  }

  @Test
  void theNativeIntegrationIsNamedNativeAndSetsUpNoConnection() {
    var integration = new NativeIntegration();

    assertThat(integration.id()).isEqualTo(NativeIntegration.NATIVE_ID);
    assertThat(integration.name()).isEqualTo("Native");
    assertThat(integration).isNotInstanceOf(IntegrationConnectionSchema.class);
  }
}
