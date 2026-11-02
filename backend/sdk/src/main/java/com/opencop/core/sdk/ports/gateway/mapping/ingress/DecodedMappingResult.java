// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.geojson.Feature;

/**
 * Normalized decode outcome used by callers that always need features and may also need structured
 * mapping diagnostics.
 *
 * <p>Some ingress mappers only produce {@link Feature} output, while engine-backed dynamic mapper
 * execution also produces a {@link MappingResponse} with record, error, and warning details.
 */
public record DecodedMappingResult(
    UUID mapperId,
    int mapperVersion,
    String mapperName,
    List<Feature> features,
    MappingResponse mappingResponse,
    DecodedOutputDisposition disposition,
    List<MappingDiagnostic> diagnostics) {

  public DecodedMappingResult {
    Objects.requireNonNull(mapperId, "mapperId");
    if (mapperVersion < 0) {
      throw new IllegalArgumentException("mapperVersion must be nonnegative");
    }
    Objects.requireNonNull(mapperName, "mapperName");
    features = List.copyOf(Objects.requireNonNull(features, "features"));
    Objects.requireNonNull(disposition, "disposition");
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    if (disposition == DecodedOutputDisposition.EMITTED && features.isEmpty()) {
      throw new IllegalArgumentException("emitted disposition requires features");
    }
    if (disposition != DecodedOutputDisposition.EMITTED && !features.isEmpty()) {
      throw new IllegalArgumentException("no-output disposition requires empty features");
    }
    if (disposition == DecodedOutputDisposition.ACCEPTED_NO_OUTPUT && mappingResponse != null) {
      throw new IllegalArgumentException("accepted no-output cannot carry a mapping response");
    }
  }

  /**
   * Preserves the pre-diagnostics constructor contract for SDK consumers that construct results
   * directly.
   */
  public DecodedMappingResult(
      UUID mapperId,
      int mapperVersion,
      String mapperName,
      List<Feature> features,
      MappingResponse mappingResponse,
      DecodedOutputDisposition disposition) {
    this(mapperId, mapperVersion, mapperName, features, mappingResponse, disposition, List.of());
  }

  /** Use when an ingress mapper only returns features and has no structured mapping response. */
  public static DecodedMappingResult fromFeatures(
      UUID mapperId, int mapperVersion, String mapperName, List<? extends Feature> features) {
    List<Feature> snapshot = List.copyOf(Objects.requireNonNull(features, "features"));
    var disposition =
        snapshot.isEmpty()
            ? DecodedOutputDisposition.NO_OUTPUT_UNCLASSIFIED
            : DecodedOutputDisposition.EMITTED;
    return new DecodedMappingResult(
        mapperId, mapperVersion, mapperName, snapshot, null, disposition, List.of());
  }

  /**
   * Use when a Java ingress mapper explicitly accepts a payload that intentionally emits no
   * features.
   */
  public static DecodedMappingResult acceptedNoOutput(
      UUID mapperId, int mapperVersion, String mapperName) {
    return new DecodedMappingResult(
        mapperId,
        mapperVersion,
        mapperName,
        List.of(),
        null,
        DecodedOutputDisposition.ACCEPTED_NO_OUTPUT,
        List.of());
  }

  /**
   * Use when a Java ingress mapper accepts a payload without output and records stable diagnostics.
   */
  public static DecodedMappingResult acceptedNoOutput(
      UUID mapperId, int mapperVersion, String mapperName, List<MappingDiagnostic> diagnostics) {
    return new DecodedMappingResult(
        mapperId,
        mapperVersion,
        mapperName,
        List.of(),
        null,
        DecodedOutputDisposition.ACCEPTED_NO_OUTPUT,
        diagnostics);
  }

  /**
   * Use when a Java ingress mapper rejects a structurally valid message during semantic mapping.
   */
  public static DecodedMappingResult invalidMapping(
      UUID mapperId, int mapperVersion, String mapperName, MappingDiagnostic diagnostic) {
    return new DecodedMappingResult(
        mapperId,
        mapperVersion,
        mapperName,
        List.of(),
        null,
        DecodedOutputDisposition.INVALID_MAPPING,
        List.of(diagnostic));
  }

  /**
   * Use when decoding runs through the dynamic mapper path and returns structured record, error,
   * and warning details.
   */
  public static DecodedMappingResult fromMappingResponse(
      UUID mapperId, int mapperVersion, String mapperName, MappingResponse mappingResponse) {
    Objects.requireNonNull(mappingResponse, "mappingResponse");
    var features = new ArrayList<Feature>();
    mappingResponse.records().forEach(mappedRecord -> features.addAll(mappedRecord.output()));
    var disposition =
        features.isEmpty()
            ? DecodedOutputDisposition.NO_OUTPUT_UNCLASSIFIED
            : DecodedOutputDisposition.EMITTED;
    return new DecodedMappingResult(
        mapperId, mapperVersion, mapperName, features, mappingResponse, disposition, List.of());
  }

  public int featureCount() {
    return features.size();
  }

  public int recordCount() {
    return mappingResponse == null ? features.size() : mappingResponse.records().size();
  }

  public int errorCount() {
    var mappingErrorCount = mappingResponse == null ? 0 : mappingResponse.errors().size();
    return mappingErrorCount + diagnosticCount(MappingDiagnosticSeverity.ERROR);
  }

  public int warningCount() {
    var mappingWarningCount = mappingResponse == null ? 0 : mappingResponse.warnings().size();
    return mappingWarningCount + diagnosticCount(MappingDiagnosticSeverity.WARNING);
  }

  public int totalWarningCount() {
    var mappingWarningCount =
        mappingResponse == null
            ? 0
            : mappingResponse.warnings().size()
                + mappingResponse.records().stream()
                    .mapToInt(mappedRecord -> mappedRecord.warnings().size())
                    .sum();
    return mappingWarningCount + diagnosticCount(MappingDiagnosticSeverity.WARNING);
  }

  public Optional<MappingError> firstError() {
    if (mappingResponse == null || mappingResponse.errors().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(mappingResponse.errors().getFirst());
  }

  /** Throws a safe failure when a Java ingress mapper has rejected a semantic mapping. */
  public void throwIfErrorDiagnostic() {
    diagnostics.stream()
        .filter(diagnostic -> diagnostic.severity() == MappingDiagnosticSeverity.ERROR)
        .findFirst()
        .ifPresent(
            diagnostic -> {
              throw new InvalidMappingDiagnosticException(diagnostic);
            });
  }

  private int diagnosticCount(MappingDiagnosticSeverity severity) {
    return (int)
        diagnostics.stream().filter(diagnostic -> diagnostic.severity() == severity).count();
  }
}
