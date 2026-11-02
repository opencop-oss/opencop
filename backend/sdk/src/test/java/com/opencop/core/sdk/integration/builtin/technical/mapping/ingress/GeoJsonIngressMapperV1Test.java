// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.ObjectIdStability;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.dynamic.DynamicIngressMapperV1;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeoJsonIngressMapperV1Test {

  private final GeoJsonIngressMapperV1 decoder = new GeoJsonIngressMapperV1();

  @Test
  void exposesMapperMetadataAndDefaultUnknownObjectIdCapability() {
    assertThat(decoder.id()).isEqualTo(GeoJsonIngressMapperV1.MAPPER_ID);
    assertThat(decoder.name()).isEqualTo("GEOJSON-FEATURE");
    assertThat(decoder.version()).isEqualTo(1);
    assertThat(decoder.objectIdStability()).isEqualTo(ObjectIdStability.UNKNOWN);
  }

  @Test
  void dynamicMappingDecoderKeepsDefaultUnknownObjectIdCapability() {
    var profile =
        MapperProfile.builder()
            .withId(UUID.randomUUID())
            .withName("dynamic")
            .withDescription(null)
            .withEditable(true)
            .withVersion(1)
            .withDynamicMapper(new DynamicMapper(UUID.randomUUID(), true, null, null, List.of()))
            .withArchived(false)
            .build();
    var dynamicDecoder = new DynamicIngressMapperV1(profile, mock(MapperFacade.class));

    assertThat(dynamicDecoder.objectIdStability()).isEqualTo(ObjectIdStability.UNKNOWN);
  }
}
