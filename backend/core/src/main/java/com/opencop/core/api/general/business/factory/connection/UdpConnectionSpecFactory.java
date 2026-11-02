// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.connection;

import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpEndpointMaterial;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;

/** Assembles the connection spec of a new UDP connection from its already-decided parts. */
public final class UdpConnectionSpecFactory {

  // Connect binds the listen port itself; a new connection stores no separate local port.
  private static final Integer NO_LOCAL_PORT = null;

  private UdpConnectionSpecFactory() {}

  public static ConnectionSpec create(UdpEndpointMaterial endpoint) {
    return new ConnectionSpec(new UdpConfig(endpoint.host(), endpoint.port(), NO_LOCAL_PORT));
  }
}
