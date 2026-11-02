// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.runtimefeature;

/** Indicates that a runtime-feature operation cannot safely proceed without waiting. */
public final class RuntimeFeatureBusyException extends RuntimeException {

  public RuntimeFeatureBusyException(Throwable cause) {
    super("Runtime feature operation is busy", cause);
  }
}
