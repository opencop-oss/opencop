// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

public class PersistenceFailureException extends RuntimeException {

  public PersistenceFailureException(String message) {
    super(message);
  }

  public PersistenceFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
