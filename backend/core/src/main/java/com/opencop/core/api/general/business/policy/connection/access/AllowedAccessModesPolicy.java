// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.util.List;
import java.util.stream.Stream;

/**
 * Which access modes a connection may take, derived from its transport and the mapping its
 * integration declares for that transport. Setup discovery publishes the same answer that
 * validation enforces, so the two cannot drift.
 *
 * <p>Only a TCP socket can be written back through; every other transport only receives. Within
 * what the transport allows, every declaration reads, since it must name at least one ingress rule,
 * and it writes only when it declares an egress rule.
 */
public final class AllowedAccessModesPolicy {

  private AllowedAccessModesPolicy() {}

  /** The modes a connection over the transport may take, in the order R, W, RW. */
  public static List<AccessMode> allowedModes(
      TransportProtocol transport, ConnectionMappingDeclaration declaration) {
    return Stream.of(AccessMode.values())
        .filter(mode -> transportAllows(transport, mode) && declarationAllows(declaration, mode))
        .toList();
  }

  /** Rejects a mode the transport and the integration's declaration do not let it take together. */
  public static void assertAllows(
      TransportProtocol transport, ConnectionMappingDeclaration declaration, AccessMode mode) {
    if (!allowedModes(transport, declaration).contains(mode)) {
      throw unsupported();
    }
  }

  /**
   * Rejects a mode the transport alone rules out, for a check that runs before the integration's
   * declaration is known or where the connection has none to consult. Whether the declaration also
   * allows it is {@link #assertAllows}'s to decide.
   */
  public static void assertTransportAllows(TransportProtocol transport, AccessMode mode) {
    if (!transportAllows(transport, mode)) {
      throw unsupported();
    }
  }

  /** {@link #assertTransportAllows(TransportProtocol, AccessMode)} for a stored connection. */
  public static void assertTransportAllows(TransportKind transport, AccessMode mode) {
    assertTransportAllows(protocol(transport), mode);
  }

  private static boolean transportAllows(TransportProtocol transport, AccessMode mode) {
    return switch (transport) {
      case TCP -> true;
      case UDP, RTSP, KAFKA -> mode == AccessMode.READ;
    };
  }

  private static boolean declarationAllows(
      ConnectionMappingDeclaration declaration, AccessMode mode) {
    return !mode.canWrite() || declaresEgress(declaration);
  }

  private static boolean declaresEgress(ConnectionMappingDeclaration declaration) {
    return switch (declaration) {
      case MapperFramingDeclaration framed -> framed.egress().isPresent();
      case MapperDeclaration _ -> false;
    };
  }

  /** The protocol a stored connection's transport kind declares its integration mappings under. */
  public static TransportProtocol protocol(TransportKind transport) {
    return switch (transport) {
      case TCP -> TransportProtocol.TCP;
      case UDP -> TransportProtocol.UDP;
      case RTSP -> TransportProtocol.RTSP;
      case KAFKA -> TransportProtocol.KAFKA;
    };
  }

  private static ConnectionManagementException unsupported() {
    return new ConnectionManagementException(ConnectionExceptionCodes.UNSUPPORTED_ACCESS_MODE);
  }
}
