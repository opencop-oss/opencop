// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.BLANK_NAME;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.DUPLICATE_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.MISSING_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.NON_POSITIVE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IngressMapperContributionPolicyTest {

  private static final UUID DUPLICATE_ID = UUID.fromString("2c852a40-9937-4c0f-9940-cc4a17e358c0");

  @Test
  void rejectsMissingUuidWithStableReason() {
    var contribution = new TestDecoder(null, "Missing identifier", 1);

    var decision = assess(contribution);

    assertThat(decision.contribution()).isSameAs(contribution);
    assertThat(decision.rejectionReason()).contains(MISSING_UUID);
    assertThat(decision.rejectionReason().orElseThrow().code()).isEqualTo("missing-uuid");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankNameWithStableReason(String name) {
    var contribution = new TestDecoder(UUID.randomUUID(), name, 1);

    var decision = assess(contribution);

    assertThat(decision.rejectionReason()).contains(BLANK_NAME);
    assertThat(decision.rejectionReason().orElseThrow().code()).isEqualTo("blank-name");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void rejectsNonPositiveVersionWithStableReason(int version) {
    var contribution = new TestDecoder(UUID.randomUUID(), "Invalid version", version);

    var decision = assess(contribution);

    assertThat(decision.rejectionReason()).contains(NON_POSITIVE_VERSION);
    assertThat(decision.rejectionReason().orElseThrow().code()).isEqualTo("non-positive-version");
  }

  @Test
  void admitsContributionWithCompleteValidMetadata() {
    var contribution = new TestDecoder(UUID.randomUUID(), "Valid mapper", 1);

    var decision = assess(contribution);

    assertThat(decision.admitted()).isTrue();
    assertThat(decision.rejectionReason()).isEmpty();
  }

  @Test
  void givesDuplicateQuarantinePrecedenceWhenOneMemberHasInvalidMetadata() {
    var validMetadata = new TestDecoder(DUPLICATE_ID, "Valid metadata", 1);
    var blankName = new TestDecoder(DUPLICATE_ID, " ", 2);

    var decisions = IngressMapperContributionPolicy.assess(List.of(validMetadata, blankName));

    assertThat(decisions)
        .extracting(decision -> decision.rejectionReason().orElseThrow())
        .containsExactly(DUPLICATE_UUID, DUPLICATE_UUID);
    assertThat(DUPLICATE_UUID.code()).isEqualTo("duplicate-uuid");
  }

  @Test
  void rejectsEveryMemberOfDuplicateGroupsLargerThanTwo() {
    var decisions =
        IngressMapperContributionPolicy.assess(
            List.of(
                new TestDecoder(DUPLICATE_ID, "First", 1),
                new TestDecoder(DUPLICATE_ID, "Second", 2),
                new TestDecoder(DUPLICATE_ID, "Third", 3)));

    assertThat(decisions)
        .hasSize(3)
        .allSatisfy(decision -> assertThat(decision.rejectionReason()).contains(DUPLICATE_UUID));
  }

  private static IngressMapperContributionDecision assess(
      PayloadIngressMapper<? extends Feature> contribution) {
    return IngressMapperContributionPolicy.assess(List.of(contribution)).getFirst();
  }

  private record TestDecoder(UUID id, String name, int version)
      implements PayloadIngressMapper<Feature> {

    @Override
    public String description() {
      return "test contribution";
    }

    @Override
    public List<Feature> map(UUID objectId, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }
}
