// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

public class WatchboxException extends GlobalOpenCopException {

  private final WatchboxErrorCode code;
  private final List<String> fields;
  private final List<String> reasons;
  private final Map<String, List<String>> context;

  public WatchboxException(
      WatchboxErrorCode code, String message, HttpStatus status, List<String> fields) {
    this(code, message, status, fields, List.of());
  }

  public WatchboxException(
      WatchboxErrorCode code,
      String message,
      HttpStatus status,
      List<String> fields,
      List<String> reasons) {
    this(code, message, status, fields, reasons, Map.of());
  }

  public WatchboxException(
      WatchboxErrorCode code,
      String message,
      HttpStatus status,
      List<String> fields,
      Map<String, List<String>> context) {
    this(code, message, status, fields, List.of(), context);
  }

  public WatchboxException(
      WatchboxErrorCode code,
      String message,
      HttpStatus status,
      List<String> fields,
      List<String> reasons,
      Map<String, List<String>> context) {
    super(code.name(), message, status);
    this.code = code;
    this.fields = fields == null ? List.of() : List.copyOf(fields);
    this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    this.context = immutableContext(context);
  }

  public HttpStatus status() {
    return getHttpStatus();
  }

  public WatchboxErrorCode code() {
    return code;
  }

  public List<String> fields() {
    return fields;
  }

  public List<String> reasons() {
    return reasons;
  }

  public Map<String, List<String>> context() {
    return context;
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
