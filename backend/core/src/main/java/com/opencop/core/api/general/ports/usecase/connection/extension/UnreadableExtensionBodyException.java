// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.extension;

/** A request body is missing, or is not a valid value of the type its operation takes. */
public final class UnreadableExtensionBodyException extends RuntimeException {

  public UnreadableExtensionBodyException(String message, Throwable cause) {
    super(message, cause);
  }
}
