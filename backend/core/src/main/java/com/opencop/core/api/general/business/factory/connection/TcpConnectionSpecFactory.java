// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.connection;

import com.opencop.core.api.general.business.model.connection.TcpTimeouts;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpEndpointMaterial;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.TcpSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;

/** Assembles the connection spec of a new TCP connection from its already-decided parts. */
public final class TcpConnectionSpecFactory {

  private static final boolean TCP_NO_DELAY = true;

  private TcpConnectionSpecFactory() {}

  /** The spec of a connection whose setup an integration resolved. */
  public static ConnectionSpec create(
      ResolvedTcpFraming framing,
      TcpEndpointMaterial endpoint,
      TcpSecurity security,
      TcpTimeouts timeouts) {
    return create(stored(framing), endpoint, security, timeouts);
  }

  public static ConnectionSpec create(
      TcpFraming framing,
      TcpEndpointMaterial endpoint,
      TcpSecurity security,
      TcpTimeouts timeouts) {
    return new ConnectionSpec(
        new TcpConfig(
            endpoint.host(),
            endpoint.port(),
            timeouts.connectTimeoutMs(),
            timeouts.readTimeoutMs(),
            TCP_NO_DELAY,
            framing,
            security));
  }

  /** The framing as a connection spec stores it. */
  private static TcpFraming stored(ResolvedTcpFraming framing) {
    return switch (framing) {
      case ResolvedTcpFraming.Custom custom ->
          new CustomTcpFraming(custom.mode(), custom.delimiter());
      case ResolvedTcpFraming.Integration integration ->
          new IntegrationTcpFraming(integration.framingId());
    };
  }
}
