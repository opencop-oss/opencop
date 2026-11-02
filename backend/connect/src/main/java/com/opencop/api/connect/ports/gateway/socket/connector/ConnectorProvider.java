// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.connector;

import com.opencop.api.connect.business.connector.ConnectorCreationContext;

/** Builds the socket connector a connection runs on, once this runtime can run it. */
public interface ConnectorProvider {

  /**
   * Whether this runtime can run the connection with the behavior it requires: always for a
   * connection without an integration, otherwise only when its exact schema version is registered.
   */
  boolean canRun(ConnectorCreationContext context);

  /**
   * Creates the connector of the connection's transport, composed with its integration behavior.
   */
  SocketConnector create(ConnectorCreationContext context);

  /**
   * Swaps the behavior plan of a running connector when the connection's effective integration
   * settings changed, keeping its socket open.
   *
   * @return whether the plan was swapped
   */
  boolean applySettings(SocketConnector connector, ConnectorCreationContext context);
}
