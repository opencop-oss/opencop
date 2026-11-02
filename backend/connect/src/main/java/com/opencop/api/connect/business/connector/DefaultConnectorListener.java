// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.api.connect.ports.usecase.event.EventHandler;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default listener that handles message forwarding for all connections.
 *
 * <p>Runtime state (status, lastMessageAt) is observed by ConnectionManager's internal
 * ManagerListener and written through SourceRuntimeObservationWriter (currently backed by
 * Postgres).
 */
public class DefaultConnectorListener implements ConnectorListener {

  private static final Logger log = LoggerFactory.getLogger(DefaultConnectorListener.class);

  private final EventHandler<byte[]> eventHandler;

  public DefaultConnectorListener(EventHandler<byte[]> eventHandler) {
    this.eventHandler = eventHandler;
  }

  @Override
  public void onMessage(UUID sourceId, byte[] data) {
    log.debug("Connection {} received message ({} bytes)", sourceId, data.length);
    eventHandler.handle(sourceId, data);
  }

  @Override
  public void onStateChange(UUID sourceId, ConnectorState state) {
    log.info("Connection {} state changed to {}", sourceId, state);
    // State tracking is handled by ConnectionManager's internal ManagerListener
  }

  @Override
  public void onError(UUID sourceId, Throwable error) {
    log.error("Connection {} encountered error: {}", sourceId, error.getMessage(), error);
    // Error tracking is handled by ConnectionManager's internal ManagerListener
  }
}
