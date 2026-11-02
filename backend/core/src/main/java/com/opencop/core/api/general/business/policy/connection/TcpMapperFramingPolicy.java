// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.connection.create.TcpFramingSelection;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.setup.ResolvedTcpFraming;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.connection.spec.transport.CustomTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.IntegrationTcpFraming;
import com.opencop.core.sdk.model.connection.spec.transport.TcpFraming;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether a TCP connection's mapper and framing satisfy its integration's TCP declaration.
 * Framing exists only for TCP, so these pair rules have no counterpart for other transports.
 *
 * <p>Declarations pair mappers with framings the integration provides in code. A connection with
 * custom framing satisfies only a declaration that permits custom framing, which an integration
 * that frames its own stream does not.
 */
public final class TcpMapperFramingPolicy {
  private TcpMapperFramingPolicy() {}

  /** Rejects a new selection whose mapper is unavailable or not declared with its framing. */
  public static void assertValid(
      MapperFramingDeclaration declaration,
      UUID mapperId,
      MapperProfile mapper,
      TcpFraming framing) {
    ConnectionExceptionCodes reason;
    if (mapperId == null || mapper == null) {
      reason = ConnectionExceptionCodes.MAPPER_NOT_FOUND;
    } else if (Boolean.TRUE.equals(mapper.archived())) {
      reason = ConnectionExceptionCodes.MAPPER_ARCHIVED;
    } else if (!permits(declaration, mapperId, framing)) {
      reason = ConnectionExceptionCodes.PAIR_NOT_ALLOWED;
    } else {
      reason = null;
    }
    if (reason != null) {
      throw new ConnectionManagementException(reason);
    }
  }

  /** Rejects a mapper and framing the declaration does not permit together. */
  public static void assertPermits(
      MapperFramingDeclaration declaration, UUID mapperId, ResolvedTcpFraming framing) {
    if (!permits(declaration, mapperId, framing)) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.PAIR_NOT_ALLOWED);
    }
  }

  /**
   * Rejects a framing the declaration's rules never mention, for a connection that pairs no ingress
   * mapper with it: writing pairs nothing, yet the stream still frames as the integration does.
   */
  public static void assertFramingPermitted(
      MapperFramingDeclaration declaration, ResolvedTcpFraming framing) {
    var permitted =
        switch (framing) {
          case ResolvedTcpFraming.Integration integration ->
              declaration.framingIds().contains(integration.framingId());
          case ResolvedTcpFraming.Custom _ ->
              declaration.rules().contains(new FramingRule.AnyMapperCustomFraming());
        };
    if (!permitted) {
      throw ConnectionManagementException.forField(
          ConnectionExceptionCodes.FRAMING_NOT_ALLOWED, TcpFramingSelection.FIELD);
    }
  }

  /**
   * Reports why an existing connection's pair no longer matches its declaration, or null when it
   * does. Existing connections keep running, so the caller only records the reason.
   */
  public static ConnectionExceptionCodes existingMismatch(
      Optional<MapperFramingDeclaration> declaration, UUID mapperId, TcpFraming framing) {
    if (declaration.isEmpty()) {
      return ConnectionExceptionCodes.INTEGRATION_DECLARATION_NOT_FOUND;
    }
    return permits(declaration.orElseThrow(), mapperId, framing)
        ? null
        : ConnectionExceptionCodes.PAIR_NOT_ALLOWED;
  }

  private static boolean permits(
      MapperFramingDeclaration declaration, UUID mapperId, ResolvedTcpFraming framing) {
    return switch (framing) {
      case ResolvedTcpFraming.Integration integration ->
          declaration.allows(mapperId, integration.framingId());
      case ResolvedTcpFraming.Custom _ -> declaration.allowsCustomFraming(mapperId);
    };
  }

  /**
   * Rejects the ingress mapper and framing a framed connection selects unless its declaration
   * permits them, whether the connection is being created or reconfigured. Without an ingress
   * mapper, a framing it has must be permitted alone. With one, the mapper must be permitted paired
   * with the framing, so a connection with no framing cannot take an ingress mapper.
   *
   * @param framing the framing the connection splits its stream with, empty when it stores none
   */
  public static void assertSelection(
      MapperFramingDeclaration declaration,
      Optional<UUID> ingressMapperId,
      Optional<ResolvedTcpFraming> framing) {
    if (ingressMapperId.isEmpty()) {
      framing.ifPresent(resolved -> assertFramingPermitted(declaration, resolved));
      return;
    }
    var paired =
        framing.orElseThrow(
            () -> new ConnectionManagementException(ConnectionExceptionCodes.PAIR_NOT_ALLOWED));
    assertPermits(declaration, ingressMapperId.orElseThrow(), paired);
  }

  /**
   * The framing a connection spec stores, as a selection resolves it; empty for a connection that
   * stores none.
   */
  public static Optional<ResolvedTcpFraming> resolved(TcpFraming stored) {
    return switch (stored) {
      case IntegrationTcpFraming integration ->
          Optional.of(new ResolvedTcpFraming.Integration(integration.framingId()));
      case CustomTcpFraming custom ->
          Optional.of(new ResolvedTcpFraming.Custom(custom.mode(), custom.delimiter()));
      case null -> Optional.empty();
    };
  }

  /**
   * The same rule for a framing a connection spec stores; a connection storing none has no pair.
   */
  private static boolean permits(
      MapperFramingDeclaration declaration, UUID mapperId, TcpFraming framing) {
    return resolved(framing)
        .map(resolved -> permits(declaration, mapperId, resolved))
        .orElse(false);
  }
}
