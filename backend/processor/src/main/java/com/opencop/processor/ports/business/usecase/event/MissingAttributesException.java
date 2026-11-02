// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

public class MissingAttributesException extends RuntimeException {
  public MissingAttributesException(String attributeName) {
    super("Missing or invalid payload attribute: " + attributeName);
  }
}
