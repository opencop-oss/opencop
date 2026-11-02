// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.udp;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;

/**
 * A UDP connection's listen port must be one a socket can bind: Connect binds it, and {@code
 * uk_data_source_listen_port_active} reserves it, so a port outside the bindable range would
 * reserve one that cannot exist.
 */
public final class UdpListenPortPolicy {

  public static final int MIN_LISTEN_PORT = 1;
  public static final int MAX_LISTEN_PORT = 65535;

  /** The declared field a UDP listener reads its port from. */
  public static final String LISTEN_PORT_FIELD = "listenPort";

  public static final String MISSING_PORT = "MISSING_UDP_PORT";
  public static final String INVALID_PORT = "INVALID_UDP_PORT";

  private static final String MISSING_PORT_MESSAGE = "A UDP listener requires a listen port";
  private static final String INVALID_PORT_MESSAGE =
      "Listen port must be between " + MIN_LISTEN_PORT + " and " + MAX_LISTEN_PORT;

  private UdpListenPortPolicy() {}

  /** The port, once it is known to be present and bindable. */
  public static int requireBindable(Integer port) throws ConnectionInputException {
    if (port == null) {
      throw rejection(MISSING_PORT, MISSING_PORT_MESSAGE);
    }
    if (!isBindable(port)) {
      throw rejection(INVALID_PORT, INVALID_PORT_MESSAGE);
    }
    return port;
  }

  /** The port a request supplied as text, once it is known to be present and bindable. */
  public static int requireBindable(String suppliedPort) throws ConnectionInputException {
    if (suppliedPort == null || suppliedPort.isBlank()) {
      throw rejection(MISSING_PORT, MISSING_PORT_MESSAGE);
    }
    try {
      return requireBindable(Integer.parseInt(suppliedPort.strip()));
    } catch (NumberFormatException _) {
      throw rejection(INVALID_PORT, INVALID_PORT_MESSAGE);
    }
  }

  private static boolean isBindable(int port) {
    return port >= MIN_LISTEN_PORT && port <= MAX_LISTEN_PORT;
  }

  private static ConnectionInputException rejection(String detailCode, String message) {
    return ConnectionInputException.rejecting(
        LISTEN_PORT_FIELD, ConnectionInputFailure.INVALID_INPUT, detailCode, message);
  }
}
