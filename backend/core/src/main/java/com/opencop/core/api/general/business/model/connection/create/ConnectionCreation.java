// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.create;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to create one connection, typed by the transport it uses. Sealed, so creating a new
 * transport's connection cannot compile until the creation dispatches it.
 */
public sealed interface ConnectionCreation {

  ConnectionCreationDetails details();

  /** The transport the connection uses. */
  TransportKind kind();

  /**
   * A TCP connection, which also says how its stream splits into messages and, when it writes, what
   * it writes with.
   *
   * @param framing the requested framing, null when the request supplied none
   * @param egressMapperId the egress mapper the connection writes with, null when none was selected
   */
  record Tcp(ConnectionCreationDetails details, TcpFramingSelection framing, UUID egressMapperId)
      implements ConnectionCreation {

    public Tcp {
      Objects.requireNonNull(details, "details");
    }

    @Override
    public TransportKind kind() {
      return TransportKind.TCP;
    }
  }

  /** A UDP connection, which needs nothing beyond the common details. */
  record Udp(ConnectionCreationDetails details) implements ConnectionCreation {

    public Udp {
      Objects.requireNonNull(details, "details");
    }

    @Override
    public TransportKind kind() {
      return TransportKind.UDP;
    }
  }

  /** An RTSP connection, which needs nothing beyond the common details. */
  record Rtsp(ConnectionCreationDetails details) implements ConnectionCreation {

    public Rtsp {
      Objects.requireNonNull(details, "details");
    }

    @Override
    public TransportKind kind() {
      return TransportKind.RTSP;
    }
  }

  /** A Kafka connection, which needs nothing beyond the common details. */
  record Kafka(ConnectionCreationDetails details) implements ConnectionCreation {

    public Kafka {
      Objects.requireNonNull(details, "details");
    }

    @Override
    public TransportKind kind() {
      return TransportKind.KAFKA;
    }
  }
}
