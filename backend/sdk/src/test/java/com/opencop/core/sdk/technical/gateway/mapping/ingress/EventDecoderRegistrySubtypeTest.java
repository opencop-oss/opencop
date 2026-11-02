// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class EventDecoderRegistrySubtypeTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID MAPPER_ID = UUID.fromString("2c852a40-9937-4c0f-9940-cc4a17e358c0");
  private static final UUID SOURCE_ID = UUID.fromString("3c80b8cb-930c-45fe-9e0b-d84460447513");

  @Test
  void acceptsAndResolvesADecoderForAFeatureSubtype() {
    var mappers = mock(MapperProfileService.class);
    when(mappers.findLatestForSource(SOURCE_ID))
        .thenReturn(
            Optional.of(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("subtype")
                    .withDescription("test")
                    .withEditable(false)
                    .withIntegrationId(TEST_INTEGRATION.id())
                    .withVersion(1)
                    .withArchived(false)
                    .build()));
    var registry =
        new EventDecoderRegistry(
            null,
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION, List.of(new SubtypeDecoder())))),
            mappers);

    assertThat(
            registry.decodeAndMap(
                UUID.randomUUID(), new MappingProperties(SOURCE_ID, null, false), "ok".getBytes()))
        .singleElement()
        .isInstanceOf(FeatureSubtype.class);
  }

  private static final class FeatureSubtype extends Feature {}

  private static final class SubtypeDecoder implements PayloadIngressMapper<FeatureSubtype> {

    @Override
    public UUID id() {
      return MAPPER_ID;
    }

    @Override
    public String name() {
      return "subtype";
    }

    @Override
    public String description() {
      return "subtype test decoder";
    }

    @Override
    public int version() {
      return 1;
    }

    @Override
    public List<FeatureSubtype> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of(new FeatureSubtype());
    }
  }
}
