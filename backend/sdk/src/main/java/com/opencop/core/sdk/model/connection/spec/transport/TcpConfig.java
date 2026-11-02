// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.security.TcpSecurity;
import java.util.List;
import java.util.Objects;

/**
 * TCP keeps legacy per-connection socket knobs for compatibility. Prefer the RTSP split for new
 * protocols: persist only values that vary by data source in JSONB, and keep connector-wide runtime
 * policy in app code or global config.
 *
 * @param framing how the byte stream is split into messages; Connect frames only from this value
 * @param security how the socket is secured; Connect picks its TCP connector from this value
 */
@JsonTypeName("tcp")
public record TcpConfig(
    String host,
    int port,
    Integer connectTimeoutMs,
    Integer readTimeoutMs,
    Boolean tcpNoDelay,
    TcpFraming framing,
    TcpSecurity security)
    implements TransportConfig {

  public TcpConfig {
    Objects.requireNonNull(security, "security");
  }

  @Override
  @JsonIgnore
  public TransportKind kind() {
    return TransportKind.TCP;
  }

  @Override
  @JsonIgnore
  public List<String> secretRefs() {
    return security.secretRefs();
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public int getPort() {
    return port;
  }

  public TcpConfig withFraming(TcpFraming framing) {
    return new TcpConfig(
        host, port, connectTimeoutMs, readTimeoutMs, tcpNoDelay, framing, security);
  }
}
