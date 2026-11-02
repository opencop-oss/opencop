// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record WatchboxErrorResponse(
    WatchboxErrorCode code,
    String message,
    List<String> fields,
    List<String> reasons,
    Map<String, List<String>> context) {

  public WatchboxErrorResponse(
      WatchboxErrorCode code, String message, List<String> fields, List<String> reasons) {
    this(code, message, fields, reasons, Map.of());
  }

  public WatchboxErrorResponse {
    fields = fields == null ? List.of() : List.copyOf(fields);
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
    context = immutableContext(context);
  }

  private static Map<String, List<String>> immutableContext(Map<String, List<String>> context) {
    if (context == null) {
      return Map.of();
    }
    var copiedContext = new LinkedHashMap<String, List<String>>();
    for (var entry : context.entrySet()) {
      copiedContext.put(
          Objects.requireNonNull(entry.getKey(), "context key"),
          List.copyOf(Objects.requireNonNull(entry.getValue(), "context value")));
    }
    return Map.copyOf(copiedContext);
  }
}
