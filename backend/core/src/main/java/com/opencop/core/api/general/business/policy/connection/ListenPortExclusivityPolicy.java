// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Decides which transports reserve their port deployment-wide, and refuses a reservation the
 * deployment has already granted.
 *
 * <p>UDP binds a local socket rather than dialling a remote endpoint, so its port is exclusive
 * deployment-wide. It is the only transport that listens today; the outbound transports (TCP, RTSP)
 * may share an endpoint freely. A transport that starts listening must be added both to {@link
 * #reservedListenPort} and to the {@code uk_data_source_listen_port_active} predicate and
 * expression — nothing forces either.
 *
 * <p>Callers own the occupancy lookup. That read is unlocked and does not close the create/create
 * race — the unique index does; this policy's job is a readable error on the ordinary path, and one
 * definition of the error for the race loser that reaches the index instead.
 */
public final class ListenPortExclusivityPolicy {

  private ListenPortExclusivityPolicy() {}

  /**
   * The port the transport binds, when it binds a local socket and so reserves that port
   * exclusively; empty for transports that only dial out.
   */
  public static OptionalInt reservedListenPort(TransportConfig transport) {
    return transport instanceof UdpConfig udp
        ? OptionalInt.of(udp.listenPort())
        : OptionalInt.empty();
  }

  /**
   * Refuses a listen port another active source already reserves.
   *
   * @param listenPort the port the new source would reserve
   * @param currentHolder the active source already holding that port, empty when it is free
   */
  public static void assertAvailable(int listenPort, Optional<UUID> currentHolder) {
    if (currentHolder.isPresent()) {
      throw portAlreadyReserved(listenPort);
    }
  }

  /**
   * The business error for a listen port the deployment has already granted. Shared by the
   * pre-check above and by callers that learn of the collision from the database instead.
   */
  public static ConnectionManagementException portAlreadyReserved(int listenPort) {
    return new ConnectionManagementException(
        ConnectionExceptionCodes.LISTEN_PORT_IN_USE, listenPort);
  }
}
