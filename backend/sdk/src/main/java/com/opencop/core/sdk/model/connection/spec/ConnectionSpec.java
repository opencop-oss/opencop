// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.opencop.core.sdk.model.connection.add.SecurityKind;
import com.opencop.core.sdk.model.connection.spec.security.MtlsConfig;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.List;

/**
 * Complete specification for a connection. Each transport carries the security it supports, so a
 * spec holds only combinations Connect can run.
 *
 * @param transport TCP, UDP, or RTSP configuration
 */
public record ConnectionSpec(TransportConfig transport) {

  /**
   * The refs of every stored secret the connection reads, which it owns: what archiving it deletes,
   * and what replacing its setup leaves behind.
   */
  @JsonIgnore
  public List<String> secretRefs() {
    return transport.secretRefs();
  }

  public SecurityKind securityType() {
    return switch (transport) {
      case TcpConfig tcp ->
          switch (tcp.security()) {
            case NoSecurity _ -> SecurityKind.NONE;
            case MtlsConfig _ -> SecurityKind.MTLS;
          };
      case UdpConfig _, RtspConfig _ -> SecurityKind.NONE;
    };
  }
}
