// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.UUID;

/**
 * Builds the connectors of one transport, covering every security that transport's spec permits.
 */
public interface ConnectorFactory {

  /** Builds the connector, including any framing its transport derives from the spec. */
  SocketConnector create(UUID sourceId, ConnectionSpec specification);
}
