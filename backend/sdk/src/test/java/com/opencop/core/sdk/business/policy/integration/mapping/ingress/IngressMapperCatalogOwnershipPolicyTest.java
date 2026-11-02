// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngressMapperCatalogOwnershipPolicyTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000808");

  @Test
  void rejectsMapperAuthoredAsData() {
    assertThat(IngressMapperCatalogOwnershipPolicy.canUseContribution(mapper(null))).isFalse();
  }

  @Test
  void rejectsMissingMapper() {
    assertThat(IngressMapperCatalogOwnershipPolicy.canUseContribution(null)).isFalse();
  }

  @Test
  void admitsMapperContributedByAnIntegration() {
    assertThat(IngressMapperCatalogOwnershipPolicy.canUseContribution(mapper(INTEGRATION_ID)))
        .isTrue();
  }

  private static MapperProfile mapper(UUID integrationId) {
    return MapperProfile.builder()
        .withId(UUID.fromString("00000000-0000-4000-8000-000000000807"))
        .withName("TEST")
        .withDescription("Catalog ownership policy fixture")
        .withEditable(false)
        .withIntegrationId(integrationId)
        .withVersion(1)
        .withArchived(false)
        .build();
  }
}
