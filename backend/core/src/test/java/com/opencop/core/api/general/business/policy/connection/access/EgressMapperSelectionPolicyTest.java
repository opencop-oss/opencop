// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EgressMapperSelectionPolicyTest {

  private static final UUID EGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
  private static final UUID OTHER_EGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
  private static final MapperFramingDeclaration READ_ONLY =
      MapperFramingDeclaration.of(new MapperFramingPair(UUID.randomUUID(), UUID.randomUUID()));

  @Test
  void permitsWhatAClosedRuleNamesAndAnythingUnderAnOpenOne() {
    assertThatNoException()
        .isThrownBy(
            () ->
                EgressMapperSelectionPolicy.assertPermits(
                    READ_ONLY.withEgress(EgressMapperRule.closedTo(Set.of(EGRESS))), EGRESS));
    assertThatNoException()
        .isThrownBy(
            () ->
                EgressMapperSelectionPolicy.assertPermits(
                    READ_ONLY.withEgress(EgressMapperRule.openMapper()), OTHER_EGRESS));
  }

  @Test
  void rejectsAnEgressMapperOutsideAClosedRuleNamingTheField() {
    assertThatThrownBy(
            () ->
                EgressMapperSelectionPolicy.assertPermits(
                    READ_ONLY.withEgress(EgressMapperRule.closedTo(Set.of(EGRESS))), OTHER_EGRESS))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception -> {
              assertThat(exception.getCode())
                  .isEqualTo(ConnectionExceptionCodes.MAPPER_NOT_ALLOWED.code());
              assertThat(exception.getField())
                  .isEqualTo(ConnectionCreationDetails.EGRESS_MAPPER_FIELD);
            });
  }

  /** An integration that declares no egress rule cannot write at all. */
  @Test
  void rejectsWritingThroughAnIntegrationWithoutAnEgressRule() {
    assertThatThrownBy(() -> EgressMapperSelectionPolicy.assertPermits(READ_ONLY, EGRESS))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            exception ->
                assertThat(exception.getCode())
                    .isEqualTo(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE.code()));
  }
}
