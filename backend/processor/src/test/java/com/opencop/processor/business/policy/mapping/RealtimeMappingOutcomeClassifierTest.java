// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnostic;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.processor.business.model.mapping.MappingDecoderKind;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcomeDecision;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class RealtimeMappingOutcomeClassifierTest {

  private static final UUID MAPPER_ID = UUID.fromString("64f67cc1-7cc8-4ea4-9b4f-aea6848e17ba");
  private final RealtimeMappingOutcomeClassifier classifier =
      new RealtimeMappingOutcomeClassifier();

  @Test
  void classifiesEmittedJavaOutput() {
    assertDecision(
        javaResult(List.of(new Feature())),
        RealtimeMappingOutcome.EMITTED,
        RealtimeMappingReason.NONE,
        MappingDecoderKind.JAVA,
        false);
  }

  @Test
  void classifiesAcceptedAndUnclassifiedJavaNoOutput() {
    assertDecision(
        DecodedMappingResult.acceptedNoOutput(MAPPER_ID, 1, "FIXTURE"),
        RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT,
        RealtimeMappingReason.EXPLICIT_DECODER_CONTRACT,
        MappingDecoderKind.JAVA,
        false);
    assertDecision(
        javaResult(List.of()),
        RealtimeMappingOutcome.NO_OUTPUT_UNCLASSIFIED,
        RealtimeMappingReason.DECODER_NO_OUTPUT,
        MappingDecoderKind.JAVA,
        true);
  }

  @Test
  void classifiesAcceptedDecoderWarningsAsAQueryableAcceptedNoOutputProblem() {
    var result =
        DecodedMappingResult.acceptedNoOutput(
            MAPPER_ID,
            1,
            "FIXTURE",
            List.of(
                new MappingDiagnostic(
                    "UNSUPPORTED_TYPE",
                    MappingDiagnosticSeverity.WARNING,
                    "Unsupported input type",
                    java.util.Map.of())));

    assertDecision(
        result,
        RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT,
        RealtimeMappingReason.ACCEPTED_DECODER_WARNING,
        MappingDecoderKind.JAVA,
        true);
  }

  @Test
  void keepsDecoderWarningReasonsGenericRegardlessOfMapperDiagnosticCode() {
    var result =
        DecodedMappingResult.acceptedNoOutput(
            MAPPER_ID,
            1,
            "FIXTURE",
            List.of(
                new MappingDiagnostic(
                    "UNSUPPORTED_FIXTURE_TYPE",
                    MappingDiagnosticSeverity.WARNING,
                    "Unsupported input type",
                    java.util.Map.of("type", "unbounded-input"))));

    assertDecision(
        result,
        RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT,
        RealtimeMappingReason.ACCEPTED_DECODER_WARNING,
        MappingDecoderKind.JAVA,
        true);
  }

  @Test
  void classifiesAnyJavaMapperErrorDiagnosticAsAFailedMapping() {
    var result =
        DecodedMappingResult.invalidMapping(
            MAPPER_ID,
            1,
            "java",
            new MappingDiagnostic(
                "MISSING_REQUIRED_FIELD",
                MappingDiagnosticSeverity.ERROR,
                "A required input field is missing",
                java.util.Map.of("field", "position")));

    assertDecision(
        result,
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
        RealtimeMappingReason.DECODER_DIAGNOSTIC,
        MappingDecoderKind.JAVA,
        true);
  }

  @Test
  void keepsDecoderErrorReasonsGenericRegardlessOfMapperDiagnosticCode() {
    var result =
        DecodedMappingResult.invalidMapping(
            MAPPER_ID,
            1,
            "FIXTURE",
            new MappingDiagnostic(
                "INVALID_FIXTURE_MAPPING",
                MappingDiagnosticSeverity.ERROR,
                "Invalid mapping",
                java.util.Map.of("field", "point")));

    assertDecision(
        result,
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
        RealtimeMappingReason.DECODER_DIAGNOSTIC,
        MappingDecoderKind.JAVA,
        true);
  }

  @Test
  void classifiesPartialAndFailedAllFromStructuredErrors() {
    assertDecision(
        dynamicResult(List.of(new Feature()), "ON_MISSING_ERROR"),
        RealtimeMappingOutcome.PARTIAL_WITH_ERRORS,
        RealtimeMappingReason.ON_MISSING_ERROR,
        MappingDecoderKind.DYNAMIC,
        true);
    assertDecision(
        dynamicResult(List.of(), "ON_MISSING_ERROR"),
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
        RealtimeMappingReason.ON_MISSING_ERROR,
        MappingDecoderKind.DYNAMIC,
        true);
  }

  @Test
  void classifiesOnlyExactProfileDisabledAsConfigSuppressed() {
    assertDecision(
        dynamicResult(List.of(), "PROFILE_DISABLED", "PROFILE_DISABLED"),
        RealtimeMappingOutcome.CONFIG_SUPPRESSED,
        RealtimeMappingReason.PROFILE_DISABLED,
        MappingDecoderKind.DYNAMIC,
        true);
    assertDecision(
        dynamicResult(List.of(), "profile_disabled"),
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
        RealtimeMappingReason.OTHER_MAPPING_ERROR,
        MappingDecoderKind.DYNAMIC,
        true);
  }

  @Test
  void mapsEveryKnownStructuredCodeAndUnknownVariantsToBoundedReasons() {
    assertReason("ON_MISSING_ERROR", RealtimeMappingReason.ON_MISSING_ERROR);
    assertReason("PAYLOAD_ERROR", RealtimeMappingReason.PAYLOAD_ERROR);
    assertReason("UNSUPPORTED_MAPPER_FORMAT", RealtimeMappingReason.UNSUPPORTED_MAPPER_FORMAT);
    assertReason("ENGINE_EXCEPTION", RealtimeMappingReason.ENGINE_EXCEPTION);
    assertReason((String) null, RealtimeMappingReason.OTHER_MAPPING_ERROR);
    assertReason("", RealtimeMappingReason.OTHER_MAPPING_ERROR);
    assertReason(" ", RealtimeMappingReason.OTHER_MAPPING_ERROR);
    assertReason("on_missing_error", RealtimeMappingReason.OTHER_MAPPING_ERROR);
    assertReason("CUSTOMER_FREE_FORM", RealtimeMappingReason.OTHER_MAPPING_ERROR);
  }

  @Test
  void repeatedNormalizedReasonStaysSpecificAndDistinctReasonsBecomeMultiple() {
    assertReason(
        new String[] {"ON_MISSING_ERROR", "ON_MISSING_ERROR"},
        RealtimeMappingReason.ON_MISSING_ERROR);
    assertReason(
        new String[] {"ON_MISSING_ERROR", "PAYLOAD_ERROR"},
        RealtimeMappingReason.MULTIPLE_MAPPING_ERRORS);
    assertReason(
        new String[] {"CUSTOM_ONE", "CUSTOM_TWO"}, RealtimeMappingReason.OTHER_MAPPING_ERROR);
    assertReason(
        new String[] {"ON_MISSING_ERROR", "CUSTOM"}, RealtimeMappingReason.MULTIPLE_MAPPING_ERRORS);
  }

  @Test
  void metricValuesAreStableAndLowercase() {
    assertThat(Arrays.stream(RealtimeMappingEntry.values()).map(RealtimeMappingEntry::value))
        .containsExactly("byod", "raw_realtime");
    assertThat(Arrays.stream(MappingResourceKind.values()).map(MappingResourceKind::value))
        .containsExactly("data_source");
    assertThat(Arrays.stream(RealtimeMappingOutcome.values()).map(RealtimeMappingOutcome::value))
        .containsExactly(
            "emitted",
            "partial_with_errors",
            "failed_all_with_errors",
            "failed_thrown",
            "config_suppressed",
            "accepted_no_output",
            "no_output_unclassified");
    assertThat(Arrays.stream(RealtimeMappingReason.values()).map(RealtimeMappingReason::value))
        .containsExactly(
            "none",
            "profile_disabled",
            "on_missing_error",
            "payload_error",
            "unsupported_mapper_format",
            "engine_exception",
            "other_mapping_error",
            "multiple_mapping_errors",
            "explicit_decoder_contract",
            "accepted_decoder_warning",
            "decoder_diagnostic",
            "decoder_no_output",
            "thrown");
    assertThat(Arrays.stream(MappingDecoderKind.values()).map(MappingDecoderKind::value))
        .containsExactly("dynamic", "java", "unknown");
  }

  @Test
  void typedAttemptRejectsMissingIdentityAndNegativeKafkaCoordinates() {
    var resource = new MappingResourceRef(MappingResourceKind.DATA_SOURCE, UUID.randomUUID());
    assertThatThrownBy(() -> new RealtimeMappingAttempt(null, resource, 0, 0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RealtimeMappingAttempt(RealtimeMappingEntry.BYOD, resource, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RealtimeMappingAttempt(RealtimeMappingEntry.BYOD, resource, 0, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void typedDecisionRejectsSemanticallyImpossibleStates() {
    assertInvalidDecision(
        RealtimeMappingOutcome.EMITTED,
        RealtimeMappingReason.PROFILE_DISABLED,
        MappingDecoderKind.DYNAMIC);
    assertInvalidDecision(
        RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT,
        RealtimeMappingReason.EXPLICIT_DECODER_CONTRACT,
        MappingDecoderKind.DYNAMIC);
    assertInvalidDecision(
        RealtimeMappingOutcome.CONFIG_SUPPRESSED,
        RealtimeMappingReason.PROFILE_DISABLED,
        MappingDecoderKind.JAVA);
    assertInvalidDecision(
        RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
        RealtimeMappingReason.PROFILE_DISABLED,
        MappingDecoderKind.DYNAMIC);
    assertInvalidDecision(
        RealtimeMappingOutcome.PARTIAL_WITH_ERRORS,
        RealtimeMappingReason.DECODER_NO_OUTPUT,
        MappingDecoderKind.DYNAMIC);
    assertInvalidDecision(
        RealtimeMappingOutcome.FAILED_THROWN,
        RealtimeMappingReason.THROWN,
        MappingDecoderKind.JAVA);
    assertInvalidDecision(
        RealtimeMappingOutcome.FAILED_THROWN,
        RealtimeMappingReason.ENGINE_EXCEPTION,
        MappingDecoderKind.UNKNOWN);
    assertInvalidDecision(
        RealtimeMappingOutcome.EMITTED, RealtimeMappingReason.NONE, MappingDecoderKind.UNKNOWN);
    assertInvalidDecision(
        RealtimeMappingOutcome.NO_OUTPUT_UNCLASSIFIED,
        RealtimeMappingReason.DECODER_NO_OUTPUT,
        MappingDecoderKind.UNKNOWN);
  }

  private void assertReason(String code, RealtimeMappingReason reason) {
    assertReason(new String[] {code}, reason);
  }

  private void assertReason(String[] codes, RealtimeMappingReason reason) {
    assertThat(classifier.classify(dynamicResult(List.of(), codes)).reason()).isEqualTo(reason);
  }

  private void assertDecision(
      DecodedMappingResult result,
      RealtimeMappingOutcome outcome,
      RealtimeMappingReason reason,
      MappingDecoderKind decoderKind,
      boolean problem) {
    var decision = classifier.classify(result);
    assertThat(decision.outcome()).isEqualTo(outcome);
    assertThat(decision.reason()).isEqualTo(reason);
    assertThat(decision.decoderKind()).isEqualTo(decoderKind);
    assertThat(decision.isProblem()).isEqualTo(problem);
  }

  private static void assertInvalidDecision(
      RealtimeMappingOutcome outcome,
      RealtimeMappingReason reason,
      MappingDecoderKind decoderKind) {
    assertThatThrownBy(() -> new RealtimeMappingOutcomeDecision(outcome, reason, decoderKind))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static DecodedMappingResult javaResult(List<Feature> features) {
    return DecodedMappingResult.fromFeatures(MAPPER_ID, 1, "java", features);
  }

  private static DecodedMappingResult dynamicResult(List<Feature> features, String... codes) {
    var records =
        features.isEmpty()
            ? List.<MappedRecord>of()
            : List.of(new MappedRecord(0, features, List.of()));
    var errors =
        Arrays.stream(codes)
            .map(code -> new MappingError(null, code, "must not escape", List.of(), null))
            .toList();
    var response = new MappingResponse(MAPPER_ID, null, records, errors, List.of());
    return DecodedMappingResult.fromMappingResponse(MAPPER_ID, 2, "dynamic", response);
  }
}
