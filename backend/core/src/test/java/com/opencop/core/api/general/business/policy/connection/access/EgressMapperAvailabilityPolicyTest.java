// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EgressMapperAvailabilityPolicyTest {

  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

  private final PayloadEgressMapper contribution = mock(PayloadEgressMapper.class);

  @Test
  void returnsAContributedUnarchivedEgressMapper() {
    assertThat(
            EgressMapperAvailabilityPolicy.requireAvailable(
                Optional.of(contribution), Optional.of(entry(MapperQualifier.EGRESS, false))))
        .isSameAs(contribution);
  }

  @Test
  void rejectsAMapperNoIntegrationContributes() {
    assertRejected(
        Optional.empty(), Optional.of(entry(MapperQualifier.EGRESS, false)), "MAPPER_NOT_FOUND");
  }

  @Test
  void rejectsAMapperMissingFromTheCatalog() {
    assertRejected(Optional.of(contribution), Optional.empty(), "MAPPER_NOT_FOUND");
  }

  @Test
  void rejectsAnIngressMapper() {
    assertRejected(
        Optional.of(contribution),
        Optional.of(entry(MapperQualifier.INGRESS, false)),
        "MAPPER_NOT_FOUND");
  }

  @Test
  void rejectsAnArchivedEgressMapper() {
    assertRejected(
        Optional.of(contribution),
        Optional.of(entry(MapperQualifier.EGRESS, true)),
        "MAPPER_ARCHIVED");
  }

  private static void assertRejected(
      Optional<PayloadEgressMapper> contribution,
      Optional<MapperProfile> catalogEntry,
      String code) {
    assertThatThrownBy(
            () -> EgressMapperAvailabilityPolicy.requireAvailable(contribution, catalogEntry))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code));
  }

  private static MapperProfile entry(MapperQualifier qualifier, boolean archived) {
    return MapperProfile.builder()
        .withId(MAPPER_ID)
        .withName("Egress")
        .withQualifier(qualifier)
        .withArchived(archived)
        .build();
  }
}
