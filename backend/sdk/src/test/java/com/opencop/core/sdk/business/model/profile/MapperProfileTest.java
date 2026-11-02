// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperProfileTest {

  @Test
  void aProfileBuiltWithoutOwnershipOrDirectionIsAnUnownedIngressMapper() {
    var profile = MapperProfile.builder().withId(UUID.randomUUID()).withName("ingress").build();

    assertThat(profile.integrationId()).isNull();
    assertThat(profile.qualifier()).isEqualTo(MapperQualifier.INGRESS);
  }

  @Test
  void aBuiltProfileKeepsItsQualifierThroughToBuilder() {
    var profile =
        MapperProfile.builder()
            .withId(UUID.randomUUID())
            .withName("egress")
            .withQualifier(MapperQualifier.EGRESS)
            .build();

    assertThat(profile.toBuilder().build().qualifier()).isEqualTo(MapperQualifier.EGRESS);
  }
}
