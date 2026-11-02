// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.OnMissing;
import com.opencop.core.sdk.model.mapper.config.JsonFormatConfig;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.model.mapper.rules.MappedTargetField;
import com.opencop.core.sdk.model.mapper.rules.MappingRule;
import com.opencop.core.sdk.model.mapper.rules.TargetField;
import com.opencop.core.sdk.model.mapper.selector.RecordSelector;
import com.opencop.core.sdk.model.mapper.source.JsonPathSource;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.technical.gateway.mapping.ingress.dynamic.DynamicIngressMapperV1;
import com.opencop.core.sdk.technical.mapper.MapperFacade;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.processor.business.model.mapping.MappingDecoderKind;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@SpringJUnitConfig(RequiredCoordinateMappingOutcomeTest.MapperTestConfig.class)
class RequiredCoordinateMappingOutcomeTest {

  private static final UUID MAPPER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID LATITUDE_RULE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000103");
  private static final UUID LONGITUDE_RULE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000104");
  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MapperFacade mapperFacade;

  private final RealtimeMappingOutcomeClassifier classifier =
      new RealtimeMappingOutcomeClassifier();

  @Test
  void combinedLocationWithoutSeparateCoordinatesFails() throws JacksonException {
    var result = decode(Map.of("location", "10.0,20.0"));
    var decision = classifier.classify(result);

    assertThat(result.features()).isEmpty();
    assertThat(result.mappingResponse().records()).isEmpty();
    assertThat(result.mappingResponse().errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("ON_MISSING_ERROR");
              assertThat(error.recordIndex()).isZero();
              assertThat(error.ruleIds()).containsExactly(LATITUDE_RULE_ID);
            });
    assertThat(decision.outcome()).isEqualTo(RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS);
    assertThat(decision.reason()).isEqualTo(RealtimeMappingReason.ON_MISSING_ERROR);
    assertThat(decision.decoderKind()).isEqualTo(MappingDecoderKind.DYNAMIC);
  }

  @Test
  void separateCoordinatesEmit() throws JacksonException {
    var result = decode(Map.of("latitude", 10.0, "longitude", 20.0));
    var decision = classifier.classify(result);

    assertThat(result.features()).hasSize(1);
    assertThat(result.mappingResponse().errors()).isEmpty();
    assertThat(decision.outcome()).isEqualTo(RealtimeMappingOutcome.EMITTED);
    assertThat(decision.reason()).isEqualTo(RealtimeMappingReason.NONE);
    assertThat(decision.decoderKind()).isEqualTo(MappingDecoderKind.DYNAMIC);
  }

  private DecodedMappingResult decode(Map<String, Object> payload) throws JacksonException {
    var profile =
        MapperProfile.builder()
            .withId(MAPPER_ID)
            .withName("Required coordinate mapper")
            .withDescription("Synthetic coordinate fixture")
            .withEditable(true)
            .withVersion(1)
            .withDynamicMapper(mapper())
            .withArchived(false)
            .build();
    var decoder = new DynamicIngressMapperV1(profile, mapperFacade);
    return decoder.mapResult(
        UUID.fromString("00000000-0000-0000-0000-000000000105"),
        new MappingProperties(SOURCE_ID, null, false),
        JSON.writeValueAsBytes(payload));
  }

  private static DynamicMapper mapper() {
    return new DynamicMapper(
        MAPPER_ID,
        true,
        new JsonFormatConfig(false),
        new RecordSelector(new JsonPathSource(""), OnMissing.ERROR),
        List.of(
            rule(LATITUDE_RULE_ID, TargetField.GEOMETRY_LAT, "/latitude", OnMissing.ERROR),
            rule(LONGITUDE_RULE_ID, TargetField.GEOMETRY_LON, "/longitude", OnMissing.ERROR)));
  }

  private static MappingRule rule(
      UUID id, TargetField target, String pointer, OnMissing onMissing) {
    return new MappingRule(
        id,
        true,
        new MappedTargetField(target),
        null,
        new JsonPathSource(pointer),
        List.of(),
        onMissing,
        null,
        null);
  }

  @Configuration
  @ComponentScan(basePackages = "com.opencop.core.sdk.technical.mapper")
  static class MapperTestConfig {}
}
