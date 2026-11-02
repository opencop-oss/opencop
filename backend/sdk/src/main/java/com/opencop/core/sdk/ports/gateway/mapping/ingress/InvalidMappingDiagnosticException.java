// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

/** Signals that an ingress mapper reported a safe, stable invalid-mapping diagnostic. */
public final class InvalidMappingDiagnosticException extends RuntimeException {

  private final String diagnosticField;

  public InvalidMappingDiagnosticException(MappingDiagnostic diagnostic) {
    this(java.util.Objects.requireNonNull(diagnostic, "diagnostic").context().get("field"));
  }

  private InvalidMappingDiagnosticException(String diagnosticField) {
    super(message(diagnosticField));
    this.diagnosticField = boundedField(diagnosticField);
  }

  /** Returns the bounded field context accepted for dead-letter metadata, when supplied. */
  public String diagnosticField() {
    return diagnosticField;
  }

  private static String message(String diagnosticField) {
    var field = boundedField(diagnosticField);
    return field == null ? "Decoder rejected mapping" : "Decoder rejected mapping field=" + field;
  }

  private static String boundedField(String field) {
    if (field == null || field.isBlank()) {
      return null;
    }
    return field.substring(0, Math.min(field.length(), 128));
  }
}
