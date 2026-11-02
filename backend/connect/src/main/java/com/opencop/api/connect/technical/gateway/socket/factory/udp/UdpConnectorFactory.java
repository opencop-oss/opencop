// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.udp;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.gateway.socket.UdpConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds UDP connectors: a datagram listener on the connection's listen port. */
@Component
public final class UdpConnectorFactory implements ConnectorFactory {

  @Override
  public SocketConnector create(UUID sourceId, ConnectionSpec spec) {
    var udpConfig = (UdpConfig) spec.transport();

    return new UdpConnector(sourceId, udpConfig.listenPort());
  }
}
