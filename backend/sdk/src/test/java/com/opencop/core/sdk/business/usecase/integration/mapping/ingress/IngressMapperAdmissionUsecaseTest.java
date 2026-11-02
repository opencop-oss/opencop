// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.usecase.integration.mapping.ingress;

import static com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision.RejectionReason.DUPLICATE_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributionDecision;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class IngressMapperAdmissionUsecaseTest {

  private static final UUID DUPLICATE_ID = UUID.fromString("2c852a40-9937-4c0f-9940-cc4a17e358c0");
  private static final UUID VALID_ID = UUID.fromString("ca8f641a-4763-4c86-b942-34ad5de4e221");

  private static final Integration INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000ff"),
          "Test integration",
          "Test integration");

  private final IngressMapperAdmissionUsecase usecase = new IngressMapperAdmissionUsecase();

  @Test
  void assemblesAdmittedAndRejectedViewsIndependentlyOfDiscoveryOrder() {
    var versionOne = new TestDecoder(DUPLICATE_ID, "Duplicate v1", 1);
    var versionTwo = new TestDecoder(DUPLICATE_ID, "Duplicate v2", 2);
    var unrelated = new TestDecoder(VALID_ID, "Unrelated", 1);

    var firstOrder = usecase.admit(group(versionOne, unrelated, versionTwo));
    var reverseOrder = usecase.admit(group(versionTwo, unrelated, versionOne));

    assertThat(firstOrder.contributions().mappers()).containsExactly(unrelated);
    assertThat(reverseOrder.contributions().mappers())
        .containsExactlyElementsOf(firstOrder.contributions().mappers());
    assertThat(reverseOrder.rejections()).isEqualTo(firstOrder.rejections());
    assertThat(firstOrder.rejections())
        .extracting(IngressMapperContributionDecision::contribution)
        .containsExactly(versionOne, versionTwo);
    assertThat(firstOrder.rejections())
        .allSatisfy(decision -> assertThat(decision.rejectionReason()).contains(DUPLICATE_UUID));
  }

  @Test
  void excludesEveryInvalidMetadataShapeWithoutBlockingValidContribution() {
    var valid = new TestDecoder(VALID_ID, "Valid mapper", 1);

    var admission =
        usecase.admit(
            group(
                new TestDecoder(null, "Missing UUID", 1),
                new TestDecoder(UUID.randomUUID(), " ", 1),
                new TestDecoder(UUID.randomUUID(), "Zero version", 0),
                new TestDecoder(UUID.randomUUID(), "Negative version", -1),
                valid));

    assertThat(admission.contributions().mappers()).containsExactly(valid);
    assertThat(admission.rejections()).hasSize(4);
  }

  @Test
  void ordersAdmittedContributionsDeterministically() {
    var first =
        new TestDecoder(UUID.fromString("00000000-0000-4000-8000-000000000001"), "First", 1);
    var second =
        new TestDecoder(UUID.fromString("00000000-0000-4000-8000-000000000002"), "Second", 1);

    var admission = usecase.admit(group(second, first));

    assertThat(admission.contributions().mappers()).containsExactly(first, second);
    assertThat(admission.rejections()).isEmpty();
  }

  @Test
  void keepsEachAdmittedMapperUnderTheIntegrationThatContributedIt() {
    var tak =
        new TestIntegration(
            UUID.fromString("00000000-0000-4000-8000-00000000000a"),
            "Test integration",
            "Test integration");
    var nativeMappers =
        new TestIntegration(
            UUID.fromString("00000000-0000-4000-8000-00000000000b"),
            "Test integration",
            "Test integration");
    var cot = new TestDecoder(UUID.fromString("00000000-0000-4000-8000-000000000001"), "COT", 1);
    var geoJson =
        new TestDecoder(UUID.fromString("00000000-0000-4000-8000-000000000002"), "GEOJSON", 1);

    var admission =
        usecase.admit(
            List.of(
                new IntegrationIngressMappers(nativeMappers, List.of(geoJson)),
                new IntegrationIngressMappers(tak, List.of(cot))));

    assertThat(admission.contributions().groups())
        .containsExactly(
            new IntegrationIngressMappers(tak, List.of(cot)),
            new IntegrationIngressMappers(nativeMappers, List.of(geoJson)));
  }

  private static List<IntegrationIngressMappers> group(TestDecoder... decoders) {
    return List.of(new IntegrationIngressMappers(INTEGRATION, List.of(decoders)));
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
