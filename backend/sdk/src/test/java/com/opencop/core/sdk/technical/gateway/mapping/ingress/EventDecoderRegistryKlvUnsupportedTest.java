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
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.KlvIngressMapperV1;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedOutputDisposition;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the registry correctly routes raw KLV bytes to {@link KlvIngressMapperV1} and
 * returns an empty list without throwing when no coordinates are present.
 */
class EventDecoderRegistryKlvUnsupportedTest {

  private static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
  private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-4000-8000-000000000101");

  private EventDecoderRegistry registry;

  @BeforeEach
  void setUp() {
    var service = mock(MapperProfileService.class);
    var mapper =
        MapperProfile.builder()
            .withId(KlvIngressMapperV1.MAPPER_ID)
            .withName(KlvIngressMapperV1.MAPPER_NAME)
            .withDescription(KlvIngressMapperV1.DESCRIPTION)
            .withEditable(false)
            .withIntegrationId(TEST_INTEGRATION.id())
            .withVersion(1)
            .withArchived(false)
            .build();

    when(service.findLatestForSource(SOURCE_ID)).thenReturn(Optional.of(mapper));

    registry =
        new EventDecoderRegistry(
            mock(MapperFacade.class),
            IngressMapperContributions.of(
                List.of(
                    new IntegrationIngressMappers(
                        TEST_INTEGRATION, List.of(new KlvIngressMapperV1())))),
            service);
  }

  @Test
  void returnsEmptyListForEmptyBytesWithNoRtspContext() {
    var props = new MappingProperties(SOURCE_ID, null, false);

    var features = registry.decodeAndMap(OBJECT_ID, props, new byte[0]);

    assertThat(features).isEmpty();

    var result = registry.decodeAndMapRealTimeResult(OBJECT_ID, props, new byte[0]);
    assertThat(result.features()).isEmpty();
    assertThat(result.disposition()).isEqualTo(DecodedOutputDisposition.NO_OUTPUT_UNCLASSIFIED);
  }
}
