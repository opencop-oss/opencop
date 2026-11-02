// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.tcp;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.resolver.TcpFramingResolver;
import com.opencop.api.connect.technical.gateway.socket.SessionOpener;
import com.opencop.api.connect.technical.gateway.socket.TcpConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds TCP connectors: frames the byte stream from the spec and opens each session with the
 * security the spec's transport carries.
 */
@Component
public final class TcpConnectorFactory implements ConnectorFactory {

  private final TcpFramingResolver framingResolver;
  private final PlainTcpSessions plainSessions;
  private final MtlsTcpSessions mtlsSessions;

  TcpConnectorFactory(
      ConnectionStore connectionStore,
      ConnectProperties connectProperties,
      TcpFramingResolver framingResolver) {
    this.framingResolver = framingResolver;
    this.plainSessions = new PlainTcpSessions(connectProperties);
    this.mtlsSessions = new MtlsTcpSessions(connectionStore, connectProperties);
  }

  @Override
  public SocketConnector create(UUID sourceId, ConnectionSpec spec) {
    var tcp = (TcpConfig) spec.transport();
    SessionOpener opener =
        switch (tcp.security()) {
          case NoSecurity _ -> plainSessions.opener(sourceId, tcp);
          case MtlsConfig mtls -> mtlsSessions.opener(sourceId, tcp, mtls);
        };
    return new TcpConnector(sourceId, framingResolver.get(sourceId, tcp.framing()), opener);
  }
}
