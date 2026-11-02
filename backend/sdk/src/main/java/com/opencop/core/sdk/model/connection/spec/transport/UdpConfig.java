// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.List;

@JsonTypeName("udp")
public record UdpConfig(String host, int port, Integer localPort) implements TransportConfig {

  @Override
  @JsonIgnore
  public TransportKind kind() {
    return TransportKind.UDP;
  }

  @Override
  @JsonIgnore
  public List<String> secretRefs() {
    return List.of();
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  /**
   * The local port this transport binds. Prefers {@code localPort}, falling back to {@code port}
   * when no dedicated local port is set.
   *
   * <p>UDP binds a local socket rather than dialling a remote endpoint, so it has a listen port
   * where the outbound transports have a remote port. Connect binds this port. The database
   * enforces its exclusivity with {@code uk_data_source_listen_port_active}, which repeats this
   * fallback over the stored spec; change both together.
   */
  public int listenPort() {
    return localPort != null ? localPort : port;
  }
}
