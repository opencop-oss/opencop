// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapperProfileBuilderTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("00000000-0000-4000-8000-000000000809");

  @Test
  void buildsAnAuditAwareProfileWithUserOwnershipByDefault() {
    var mapperId = UUID.randomUUID();
    var actorId = UUID.randomUUID();
    var now = Instant.parse("2026-09-11T12:00:00Z");

    var profile =
        MapperProfile.builder()
            .withId(mapperId)
            .withName("User mapper")
            .withDescription("description")
            .withEditable(true)
            .withVersion(3)
            .withArchived(false)
            .withCreatedAt(now)
            .withCreatedBy(actorId)
            .withUpdatedAt(now)
            .withUpdatedBy(actorId)
            .withLastEventAt(now)
            .build();

    assertThat(profile.integrationId()).isNull();
    assertThat(profile.createdAt()).isEqualTo(now);
    assertThat(profile.createdBy()).isEqualTo(actorId);
  }

  @Test
  void toBuilderCopiesTheProfileBeforeChangingItsOwningIntegration() {
    var original =
        MapperProfile.builder()
            .withId(UUID.randomUUID())
            .withName("Plugin mapper")
            .withDescription("description")
            .withEditable(false)
            .withVersion(7)
            .withArchived(false)
            .build();

    var plugin = original.toBuilder().withIntegrationId(INTEGRATION_ID).build();

    assertThat(plugin)
        .usingRecursiveComparison()
        .ignoringFields("integrationId")
        .isEqualTo(original);
    assertThat(plugin.integrationId()).isEqualTo(INTEGRATION_ID);
  }
}
