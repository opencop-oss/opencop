// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.usecase.connection;

import java.util.UUID;

/**
 * Thrown when a connection operation (add, reconnect) is still in progress after timeout. This is
 * not an error - the operation continues asynchronously. Endpoints should catch this and return 202
 * Accepted.
 */
public class ConnectionStillConnectingException extends RuntimeException {

  private final UUID sourceId;

  public ConnectionStillConnectingException(UUID sourceId) {
    super("Connection " + sourceId + " is still connecting");
    this.sourceId = sourceId;
  }

  public UUID getSourceId() {
    return sourceId;
  }
}
