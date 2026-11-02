// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperProfileResponseFactoryTest {

  private final MapperProfileResponseFactory factory = new MapperProfileResponseFactory();

  @Test
  void responseExposesTheMappersQualifier() {
    var profile =
        MapperProfile.builder()
            .withId(UUID.randomUUID())
            .withName("Plugin mapper")
            .withDescription("Provider owned")
            .withEditable(false)
            .withQualifier(MapperQualifier.EGRESS)
            .withVersion(1)
            .withArchived(false)
            .build();

    assertThat(factory.create(profile).qualifier()).isEqualTo(MapperQualifier.EGRESS);
  }
}
