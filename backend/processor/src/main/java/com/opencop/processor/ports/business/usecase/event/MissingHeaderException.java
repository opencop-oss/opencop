// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

public class MissingHeaderException extends RuntimeException {

  public MissingHeaderException(String headerName) {
    super("Missing or invalid Kafka header: " + headerName);
  }
}
