// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.mapping.ingress;

public class RawDataParsingException extends RuntimeException {

  public RawDataParsingException(String objectId) {
    super("Could not decode and map event id: " + objectId);
  }

  public RawDataParsingException(String objectId, Throwable cause) {
    super("Could not decode and map event id: " + objectId, cause);
  }
}
