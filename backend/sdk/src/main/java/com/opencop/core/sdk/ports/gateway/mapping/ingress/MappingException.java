// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

/** Generic mapper-boundary failure with a stable category for unsupported or invalid output. */
public class MappingException extends RuntimeException {
  public enum Code {
    UNSUPPORTED_OUTBOUND_MAPPING,
    INVALID_OUTBOUND_MAPPING
  }

  private final Code code;

  public MappingException(String message) {
    this(Code.UNSUPPORTED_OUTBOUND_MAPPING, message);
  }

  public MappingException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
