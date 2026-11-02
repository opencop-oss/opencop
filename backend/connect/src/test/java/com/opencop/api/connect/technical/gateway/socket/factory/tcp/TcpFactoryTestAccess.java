// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.tcp;

import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.resolver.TcpFramingResolver;
import com.opencop.core.sdk.ports.gateway.secrets.ConnectionStore;

/** Test-only access to the package-private TCP connector factory constructor. */
public final class TcpFactoryTestAccess {

  private TcpFactoryTestAccess() {}

  public static TcpConnectorFactory tcpConnectorFactory(
      ConnectionStore connectionStore,
      ConnectProperties connectProperties,
      TcpFramingResolver framingResolver) {
    return new TcpConnectorFactory(connectionStore, connectProperties, framingResolver);
  }
}
