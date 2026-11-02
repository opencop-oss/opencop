// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Objects;

/**
 * One more specific fact about an API error, which any route may attach to enrich it: one of
 * several rejected inputs, a conflicting resource, a limit that was exceeded.
 *
 * <p>A route adds details instead of new response fields, so every error keeps one shape.
 *
 * @param code stable code a client branches on
 * @param message safe, human-readable explanation
 * @param target what the detail concerns, such as a request field path, a parameter or a resource;
 *     null when it concerns the request as a whole
 * @param metadata further safe, structured facts keyed by name, such as an allowed maximum; null or
 *     empty when there are none. Values must be JSON-serialisable and contain no secrets.
 */
public record ErrorDetail(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) String target,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata) {

  public ErrorDetail {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
