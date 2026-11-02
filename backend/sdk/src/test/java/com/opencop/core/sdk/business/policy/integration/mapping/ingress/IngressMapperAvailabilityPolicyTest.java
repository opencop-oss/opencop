// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IngressMapperAvailabilityPolicyTest {

  @Test
  void rejectsArchivedMapper() {
    assertThat(IngressMapperAvailabilityPolicy.isAvailable(mapper(true))).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  void admitsMapperThatIsNotArchived(Boolean archived) {
    assertThat(IngressMapperAvailabilityPolicy.isAvailable(mapper(archived))).isTrue();
  }

  private static MapperProfile mapper(Boolean archived) {
    return MapperProfile.builder()
        .withId(UUID.fromString("00000000-0000-4000-8000-000000000806"))
        .withName("TEST")
        .withDescription("Availability policy fixture")
        .withEditable(false)
        .withVersion(1)
        .withArchived(archived)
        .build();
  }
}
