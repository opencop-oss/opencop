// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.MappingWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;

class DecodedMappingResultTest {

  private static final UUID MAPPER_ID = UUID.fromString("0f1d3e2a-a94a-4d1a-b109-b3387d424841");

  @Test
  void snapshotsFeatureOutputAndClassifiesItsDisposition() {
    var mutable = new ArrayList<Feature>();
    var empty = DecodedMappingResult.fromFeatures(MAPPER_ID, 3, "java", mutable);
    mutable.add(new Feature());

    var emitted = DecodedMappingResult.fromFeatures(MAPPER_ID, 3, "java", mutable);

    assertThat(empty.features()).isEmpty();
    assertThat(empty.disposition()).isEqualTo(DecodedOutputDisposition.NO_OUTPUT_UNCLASSIFIED);
    assertThat(emitted.features()).hasSize(1);
    assertThat(emitted.disposition()).isEqualTo(DecodedOutputDisposition.EMITTED);
    assertThatThrownBy(() -> emitted.features().add(new Feature()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preservesStructuredResponseAndSeparatesLegacyFromTotalWarnings() {
    var topLevelWarning = new MappingWarning("top", "ignored", List.of(), null);
    var nestedWarning = new MappingWarning("nested", "ignored", List.of(), 0);
    var feature = new Feature();
    var response =
        new MappingResponse(
            MAPPER_ID,
            null,
            List.of(new MappedRecord(0, List.of(feature), List.of(nestedWarning))),
            List.of(),
            List.of(topLevelWarning));

    var result = DecodedMappingResult.fromMappingResponse(MAPPER_ID, 7, "dynamic", response);

    assertThat(result.features()).containsExactly(feature);
    assertThat(result.mappingResponse()).isSameAs(response);
    assertThat(result.mapperVersion()).isEqualTo(7);
    assertThat(result.warningCount()).isEqualTo(1);
    assertThat(result.totalWarningCount()).isEqualTo(2);
  }

  @Test
  void acceptedNoOutputIsExplicitAndRejectsInconsistentStates() {
    var accepted = DecodedMappingResult.acceptedNoOutput(MAPPER_ID, 1, "COT");

    assertThat(accepted.features()).isEmpty();
    assertThat(accepted.disposition()).isEqualTo(DecodedOutputDisposition.ACCEPTED_NO_OUTPUT);
    assertThatThrownBy(
            () ->
                new DecodedMappingResult(
                    MAPPER_ID,
                    1,
                    "bad",
                    List.of(),
                    null,
                    DecodedOutputDisposition.EMITTED,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DecodedMappingResult.fromFeatures(MAPPER_ID, -1, "bad", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesTheLegacySixArgumentConstructorWithNoDiagnostics() {
    var result =
        new DecodedMappingResult(
            MAPPER_ID,
            1,
            "legacy",
            List.of(),
            null,
            DecodedOutputDisposition.NO_OUTPUT_UNCLASSIFIED);

    assertThat(result.diagnostics()).isEmpty();
  }

  @Test
  void carriesImmutableMapperOwnedDiagnosticsWithoutRequiringADynamicMappingResponse() {
    var diagnostic =
        new MappingDiagnostic(
            "MISSING_REQUIRED_FIELD",
            MappingDiagnosticSeverity.ERROR,
            "A required input field is missing",
            java.util.Map.of("field", "position"));

    var result = DecodedMappingResult.invalidMapping(MAPPER_ID, 1, "java", diagnostic);

    assertThat(result.mappingResponse()).isNull();
    assertThat(result.diagnostics()).containsExactly(diagnostic);
    assertThatThrownBy(() -> result.diagnostics().add(diagnostic))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void includesDiagnosticSeveritiesInOperationalCountsAndPreservesOnlyFieldOnFailure() {
    var error =
        new MappingDiagnostic(
            "INVALID_COT_MAPPING",
            MappingDiagnosticSeverity.ERROR,
            "Invalid mapping",
            java.util.Map.of("field", "detail.shape", "type", "u-d-c-c", "uid", "secret"));
    var warning =
        new MappingDiagnostic(
            "UNSUPPORTED_COT_TYPE",
            MappingDiagnosticSeverity.WARNING,
            "Unsupported type",
            java.util.Map.of("type", "a-f-G-U-C"));
    var result =
        new DecodedMappingResult(
            MAPPER_ID,
            1,
            "COT",
            List.of(),
            null,
            DecodedOutputDisposition.INVALID_MAPPING,
            List.of(error, warning));

    assertThat(result.errorCount()).isEqualTo(1);
    assertThat(result.warningCount()).isEqualTo(1);
    assertThat(result.totalWarningCount()).isEqualTo(1);
    assertThatThrownBy(result::throwIfErrorDiagnostic)
        .isInstanceOf(InvalidMappingDiagnosticException.class)
        .satisfies(
            thrown -> {
              var failure = (InvalidMappingDiagnosticException) thrown;
              assertThat(failure.diagnosticField()).isEqualTo("detail.shape");
              assertThat(failure.getMessage()).contains("field=detail.shape");
              assertThat(failure.getMessage())
                  .doesNotContain("INVALID_COT_MAPPING", "u-d-c-c", "secret");
            });
  }
}
