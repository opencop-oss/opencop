// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.usecase.integration.mapping.egress;

import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.BLANK_NAME;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.DUPLICATE_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.MISSING_UUID;
import static com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision.RejectionReason.NON_POSITIVE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.egress.IntegrationEgressMappers;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class EgressMapperAdmissionUsecaseTest {

  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");
  private static final Integration OTHER_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000bb"),
          "Test integration",
          "Test integration");
  private static final UUID DUPLICATE_ID = UUID.fromString("2c852a40-9937-4c0f-9940-cc4a17e358c0");
  private static final UUID VALID_ID = UUID.fromString("ca8f641a-4763-4c86-b942-34ad5de4e221");

  private final EgressMapperAdmissionUsecase usecase = new EgressMapperAdmissionUsecase();

  @Test
  void skipsEveryInvalidIdentityWithoutBlockingAValidContribution() {
    var valid = new TestMapper(VALID_ID, "Valid egress", 1);

    var admission =
        usecase.admit(
            List.of(
                new IntegrationEgressMappers(
                    INTEGRATION,
                    List.of(
                        new TestMapper(null, "Missing UUID", 1),
                        new TestMapper(UUID.randomUUID(), " ", 1),
                        new TestMapper(UUID.randomUUID(), "Zero version", 0),
                        valid))));

    assertThat(admission.contributions().find(VALID_ID)).containsSame(valid);
    assertThat(admission.rejections())
        .extracting(decision -> decision.rejectionReason().orElseThrow())
        .containsExactlyInAnyOrder(MISSING_UUID, BLANK_NAME, NON_POSITIVE_VERSION);
  }

  @Test
  void rejectsEveryEgressMapperSharingAnIdentifierAcrossIntegrations() {
    var first = new TestMapper(DUPLICATE_ID, "First", 1);
    var second = new TestMapper(DUPLICATE_ID, "Second", 1);
    var unrelated = new TestMapper(VALID_ID, "Unrelated", 1);

    var admission =
        usecase.admit(
            List.of(
                new IntegrationEgressMappers(INTEGRATION, List.of(first, unrelated)),
                new IntegrationEgressMappers(OTHER_INTEGRATION, List.of(second))));

    assertThat(admission.contributions().find(DUPLICATE_ID)).isEmpty();
    assertThat(admission.contributions().find(VALID_ID)).containsSame(unrelated);
    assertThat(admission.rejections())
        .extracting(EgressMapperContributionDecision::contribution)
        .containsExactlyInAnyOrder(first, second);
    assertThat(admission.rejections())
        .allSatisfy(decision -> assertThat(decision.rejectionReason()).contains(DUPLICATE_UUID));
  }

  @Test
  void keepsEachAdmittedMapperUnderTheIntegrationThatContributedIt() {
    var mapper = new TestMapper(VALID_ID, "Valid egress", 1);

    var admission =
        usecase.admit(List.of(new IntegrationEgressMappers(INTEGRATION, List.of(mapper))));

    assertThat(admission.contributions().groups())
        .containsExactly(new IntegrationEgressMappers(INTEGRATION, List.of(mapper)));
    assertThat(admission.rejections()).isEmpty();
  }

  private record TestMapper(UUID id, String name, int version) implements PayloadEgressMapper {

    @Override
    public String description() {
      return "test contribution";
    }

    @Override
    public byte[] map(Feature feature) {
      return new byte[0];
    }
  }
}
