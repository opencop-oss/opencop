// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.mapping;

import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedOutputDisposition;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.processor.business.model.mapping.MappingDecoderKind;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcome;
import com.opencop.processor.business.model.mapping.RealtimeMappingOutcomeDecision;
import com.opencop.processor.business.model.mapping.RealtimeMappingReason;
import java.util.HashSet;
import java.util.Objects;

public final class RealtimeMappingOutcomeClassifier {

  public RealtimeMappingOutcomeDecision classify(DecodedMappingResult result) {
    Objects.requireNonNull(result, "result");
    // Only JSON-rule-backed execution currently supplies a MappingResponse. Revisit this
    // discriminator if a registered Java decoder begins returning structured mapping diagnostics.
    var decoderKind =
        result.mappingResponse() == null ? MappingDecoderKind.JAVA : MappingDecoderKind.DYNAMIC;
    var hasFeatures = !result.features().isEmpty();
    var hasErrors =
        result.mappingResponse() != null && !result.mappingResponse().errors().isEmpty();

    if (result.diagnostics().stream()
        .anyMatch(diagnostic -> diagnostic.severity() == MappingDiagnosticSeverity.ERROR)) {
      return decision(
          RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS,
          RealtimeMappingReason.DECODER_DIAGNOSTIC,
          decoderKind);
    }

    if (hasFeatures) {
      return hasErrors
          ? decision(
              RealtimeMappingOutcome.PARTIAL_WITH_ERRORS,
              structuredErrorReason(result),
              decoderKind)
          : decision(RealtimeMappingOutcome.EMITTED, RealtimeMappingReason.NONE, decoderKind);
    }
    if (hasErrors) {
      var reason = structuredErrorReason(result);
      var outcome =
          reason == RealtimeMappingReason.PROFILE_DISABLED
              ? RealtimeMappingOutcome.CONFIG_SUPPRESSED
              : RealtimeMappingOutcome.FAILED_ALL_WITH_ERRORS;
      return decision(outcome, reason, decoderKind);
    }
    if (result.disposition() == DecodedOutputDisposition.ACCEPTED_NO_OUTPUT) {
      return decision(
          RealtimeMappingOutcome.ACCEPTED_NO_OUTPUT, acceptedNoOutputReason(result), decoderKind);
    }
    return decision(
        RealtimeMappingOutcome.NO_OUTPUT_UNCLASSIFIED,
        RealtimeMappingReason.DECODER_NO_OUTPUT,
        decoderKind);
  }

  private static RealtimeMappingReason acceptedNoOutputReason(DecodedMappingResult result) {
    return result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.severity() == MappingDiagnosticSeverity.WARNING)
        ? RealtimeMappingReason.ACCEPTED_DECODER_WARNING
        : RealtimeMappingReason.EXPLICIT_DECODER_CONTRACT;
  }

  private static RealtimeMappingOutcomeDecision decision(
      RealtimeMappingOutcome outcome,
      RealtimeMappingReason reason,
      MappingDecoderKind decoderKind) {
    return new RealtimeMappingOutcomeDecision(outcome, reason, decoderKind);
  }

  private static RealtimeMappingReason structuredErrorReason(DecodedMappingResult result) {
    var reasons = new HashSet<RealtimeMappingReason>();
    for (MappingError error : result.mappingResponse().errors()) {
      reasons.add(normalize(error.code()));
    }
    return reasons.size() == 1
        ? reasons.iterator().next()
        : RealtimeMappingReason.MULTIPLE_MAPPING_ERRORS;
  }

  private static RealtimeMappingReason normalize(String code) {
    return switch (code) {
      case "PROFILE_DISABLED" -> RealtimeMappingReason.PROFILE_DISABLED;
      case "ON_MISSING_ERROR" -> RealtimeMappingReason.ON_MISSING_ERROR;
      case "PAYLOAD_ERROR" -> RealtimeMappingReason.PAYLOAD_ERROR;
      case "UNSUPPORTED_MAPPER_FORMAT" -> RealtimeMappingReason.UNSUPPORTED_MAPPER_FORMAT;
      case "ENGINE_EXCEPTION" -> RealtimeMappingReason.ENGINE_EXCEPTION;
      case null, default -> RealtimeMappingReason.OTHER_MAPPING_ERROR;
    };
  }
}
