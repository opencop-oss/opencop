// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ConnectorListener {

  Logger log = LoggerFactory.getLogger(ConnectorListener.class);

  default void onStateChange(UUID sourceId, ConnectorState state) {
    log.info("State changed to {} for source {}", state, sourceId);
  }

  default void onMessage(UUID sourceId, byte[] data) {
    // WARNING: payload logging may be useful for debugging but is not advised (security,
    // performance)
    log.trace("Connection {} received payload: {}", sourceId, data);
  }

  default void onError(UUID sourceId, Throwable error) {
    log.error("Error with source {} | error: {}", sourceId, error.getMessage());
  }

  /**
   * Called when a connector requests a local runtime bounce, such as after its receive-idle
   * reconnect threshold expires.
   *
   * <p>Manual and operator restarts do not use this callback. They persist a restart epoch that is
   * applied through desired-ownership reconciliation.
   *
   * @param sourceId the connection requesting the restart
   * @param reason the connector's reason for requesting the restart
   */
  default void onRestartRequested(UUID sourceId, Throwable reason) {
    var message = reason == null ? "unspecified" : reason.getMessage();
    log.warn("Restart requested for source {} | reason: {}", sourceId, message);
  }
}
