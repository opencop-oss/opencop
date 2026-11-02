// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.usecase.integration.mapping.ingress.IngressMapperAdmissionUsecase;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class EventDecoderRegistryDuplicateIdTest {

  private static final UUID DUPLICATE_ID = UUID.fromString("2c852a40-9937-4c0f-9940-cc4a17e358c0");
  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");
  private static final Integration OTHER_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000bb"),
          "Test integration",
          "Test integration");
  private static final UUID UNRELATED_ID = UUID.fromString("ca8f641a-4763-4c86-b942-34ad5de4e221");

  @Test
  void quarantinesDuplicateIdsBeforeRegistryConstruction() {
    var first = new FirstDecoder();
    var second = new SecondDecoder();
    var unrelated = new UnrelatedDecoder();
    var contributions =
        new IngressMapperAdmissionUsecase()
            .admit(
                List.of(
                    new IntegrationIngressMappers(INTEGRATION, List.of(first, unrelated)),
                    new IntegrationIngressMappers(OTHER_INTEGRATION, List.of(second))))
            .contributions();

    assertThat(contributions.mappers()).containsExactly(unrelated);
    assertThatCode(
            () ->
                new EventDecoderRegistry(
                    mock(MapperFacade.class), contributions, mock(MapperProfileService.class)))
        .doesNotThrowAnyException();
  }

  private abstract static class TestDecoder implements PayloadIngressMapper<Feature> {

    @Override
    public UUID id() {
      return DUPLICATE_ID;
    }

    @Override
    public String name() {
      return getClass().getSimpleName();
    }

    @Override
    public String description() {
      return "duplicate ID test decoder";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }

  private static final class FirstDecoder extends TestDecoder {}

  private static final class SecondDecoder extends TestDecoder {}

  private static final class UnrelatedDecoder extends TestDecoder {

    @Override
    public UUID id() {
      return UNRELATED_ID;
    }
  }
}
