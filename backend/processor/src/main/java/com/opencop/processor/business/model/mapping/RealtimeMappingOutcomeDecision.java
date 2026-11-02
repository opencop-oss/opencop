// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.mapping;

import java.util.Objects;

public record RealtimeMappingOutcomeDecision(
    RealtimeMappingOutcome outcome, RealtimeMappingReason reason, MappingDecoderKind decoderKind) {

  public RealtimeMappingOutcomeDecision {
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(decoderKind, "decoderKind");
    var valid =
        switch (outcome) {
          case EMITTED ->
              reason == RealtimeMappingReason.NONE && decoderKind != MappingDecoderKind.UNKNOWN;
          case PARTIAL_WITH_ERRORS ->
              decoderKind == MappingDecoderKind.DYNAMIC && isStructuredErrorReason(reason);
          case FAILED_ALL_WITH_ERRORS ->
              reason != RealtimeMappingReason.PROFILE_DISABLED
                  && ((decoderKind == MappingDecoderKind.DYNAMIC && isStructuredErrorReason(reason))
                      || (decoderKind == MappingDecoderKind.JAVA
                          && reason == RealtimeMappingReason.DECODER_DIAGNOSTIC));
          case FAILED_THROWN ->
              decoderKind == MappingDecoderKind.UNKNOWN && reason == RealtimeMappingReason.THROWN;
          case CONFIG_SUPPRESSED ->
              decoderKind == MappingDecoderKind.DYNAMIC
                  && reason == RealtimeMappingReason.PROFILE_DISABLED;
          case ACCEPTED_NO_OUTPUT ->
              decoderKind == MappingDecoderKind.JAVA
                  && (reason == RealtimeMappingReason.EXPLICIT_DECODER_CONTRACT
                      || reason == RealtimeMappingReason.ACCEPTED_DECODER_WARNING);
          case NO_OUTPUT_UNCLASSIFIED ->
              reason == RealtimeMappingReason.DECODER_NO_OUTPUT
                  && decoderKind != MappingDecoderKind.UNKNOWN;
        };
    if (!valid) {
      throw new IllegalArgumentException(
          "invalid real-time mapping outcome decision: "
              + outcome
              + "/"
              + reason
              + "/"
              + decoderKind);
    }
  }

  public boolean isProblem() {
    return switch (outcome) {
      case PARTIAL_WITH_ERRORS,
          FAILED_ALL_WITH_ERRORS,
          FAILED_THROWN,
          CONFIG_SUPPRESSED,
          NO_OUTPUT_UNCLASSIFIED ->
          true;
      case EMITTED -> false;
      case ACCEPTED_NO_OUTPUT -> reason == RealtimeMappingReason.ACCEPTED_DECODER_WARNING;
    };
  }

  private static boolean isStructuredErrorReason(RealtimeMappingReason reason) {
    return switch (reason) {
      case PROFILE_DISABLED,
          ON_MISSING_ERROR,
          PAYLOAD_ERROR,
          UNSUPPORTED_MAPPER_FORMAT,
          ENGINE_EXCEPTION,
          OTHER_MAPPING_ERROR,
          MULTIPLE_MAPPING_ERRORS ->
          true;
      case NONE,
          EXPLICIT_DECODER_CONTRACT,
          ACCEPTED_DECODER_WARNING,
          DECODER_DIAGNOSTIC,
          DECODER_NO_OUTPUT,
          THROWN ->
          false;
    };
  }
}
