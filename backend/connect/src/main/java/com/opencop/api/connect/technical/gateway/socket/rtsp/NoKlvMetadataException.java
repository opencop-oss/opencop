// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

public final class NoKlvMetadataException extends RuntimeException {

  public NoKlvMetadataException(String message) {
    super(message);
  }

  public NoKlvMetadataException(String message, Throwable cause) {
    super(message, cause);
  }
}
