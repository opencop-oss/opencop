// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.mapping.ingress;

/** Signals that a mapper rejected input using a mapper-neutral failure category. */
public final class MappingInputException extends RuntimeException {

  public enum Reason {
    UNKNOWN,
    MALFORMED_PAYLOAD,
    INPUT_TOO_LARGE,
    UNSAFE_INPUT,
    STRUCTURAL_LIMIT_EXCEEDED,
    INVALID_ROOT,
    MAPPER_CONFIGURATION
  }

  private final Reason reason;

  public MappingInputException() {
    this(Reason.UNKNOWN);
  }

  public MappingInputException(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
