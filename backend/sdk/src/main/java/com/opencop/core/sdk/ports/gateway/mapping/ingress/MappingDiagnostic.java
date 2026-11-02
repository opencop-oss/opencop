// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

import java.util.Map;
import java.util.Objects;

/** A stable, mapper-owned diagnostic independent of a particular mapping-engine response model. */
public record MappingDiagnostic(
    String code, MappingDiagnosticSeverity severity, String message, Map<String, String> context) {

  public MappingDiagnostic {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    context = Map.copyOf(Objects.requireNonNull(context, "context"));
  }
}
