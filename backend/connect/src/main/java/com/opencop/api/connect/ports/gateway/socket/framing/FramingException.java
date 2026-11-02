// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.framing;

import java.io.IOException;

/**
 * Exception thrown when message framing is corrupted or invalid.
 *
 * <p>Examples: - Message exceeds maximum size limit - Invalid length prefix - Malformed delimiter
 */
public class FramingException extends IOException {

  public FramingException(String message) {
    super(message);
  }

  public FramingException(String message, Throwable cause) {
    super(message, cause);
  }
}
