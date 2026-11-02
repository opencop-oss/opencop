// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.secrets;

/** A connection's stored mTLS material is missing or cannot be turned into a TLS context. */
public final class MtlsMaterialException extends RuntimeException {

  MtlsMaterialException(String message) {
    super(message);
  }

  MtlsMaterialException(String message, Throwable cause) {
    super(message, cause);
  }
}
