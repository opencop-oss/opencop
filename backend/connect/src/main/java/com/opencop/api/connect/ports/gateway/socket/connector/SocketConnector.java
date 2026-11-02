// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.ports.gateway.socket.connector;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.util.UUID;

public interface SocketConnector extends AutoCloseable {

  UUID id();

  ConnectorState state();

  void start();

  boolean stop();

  default void send(byte[] data) throws Exception {
    throw new UnsupportedOperationException();
  }

  void addListener(ConnectorListener listener);

  void removeListener(ConnectorListener listener);

  @Override
  default void close() {
    stop();
  }
}
