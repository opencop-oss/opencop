// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.fixture;

import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Shared localhost peers and connection defaults for socket component tests. */
public final class SocketFixtures implements TestFixture {

  public static final TcpFraming NEWLINE_SPLIT =
      new CustomTcpFraming(DelimiterModes.DELIMITER_SPLIT, "\n");

  private final List<AutoCloseable> resources = new ArrayList<>();

  public TcpTestServer startTcpServer() throws IOException {
    var server = new TcpTestServer();
    resources.add(server);
    return server;
  }

  public UdpTestPeer startUdpPeer() throws IOException {
    var peer = new UdpTestPeer();
    resources.add(peer);
    return peer;
  }

  public ConnectionSpec tcpSpec(int port) {
    return tcpSpec("127.0.0.1", port);
  }

  public ConnectionSpec tcpSpec(String host, int port) {
    return tcpSpec(host, port, NEWLINE_SPLIT);
  }

  public ConnectionSpec tcpSpec(int port, TcpFraming framing) {
    return tcpSpec("127.0.0.1", port, framing);
  }

  public ConnectionSpec tcpSpec(String host, int port, TcpFraming framing) {
    return new ConnectionSpec(
        new TcpConfig(host, port, 3000, 250, true, framing, new NoSecurity()));
  }

  public ConnectionSpec udpSpec(int port) {
    return udpSpec("127.0.0.1", port);
  }

  public ConnectionSpec udpSpec(String host, int port) {
    return udpSpec(host, port, port);
  }

  public ConnectionSpec udpSpec(String host, int remotePort, int localPort) {
    return new ConnectionSpec(new UdpConfig(host, remotePort, localPort));
  }

  public byte[] defaultPayload() {
    return utf8("connect-component-payload");
  }

  public byte[] defaultCommand() {
    return utf8("connect-component-command");
  }

  public byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public void reset() {
    for (int i = resources.size() - 1; i >= 0; i--) {
      try {
        resources.get(i).close();
      } catch (Exception _) {
      }
    }
    resources.clear();
  }
}
