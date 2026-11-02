// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Instant;
import java.util.UUID;

/**
 * Runtime status of a connection - owned by connect service.
 *
 * <p>This represents the live state of a socket connection, as opposed to DataSource which is
 * static metadata. Socket status is read from Postgres runtime observations written by Connect.
 *
 * <p>The instanceId field identifies the runtime instance that observed this status (for example,
 * an ephemeral Connect worker id such as "connect-670cebba"). This enables horizontal scaling with
 * multiple service instances.
 *
 * <p>lastMessageToken is the identity of the newest inbound message this status reports, and
 * changes only when a new message is accepted — a heartbeat reporting no new traffic carries the
 * previous value forward. It exists because a timestamp cannot serve as a message identity: two
 * messages can share a millisecond, and a corrected host clock can move lastMessageAt backwards.
 * Must be unique per observing instance, since instances report the same source independently.
 */
public record RuntimeStatus(
    UUID id,
    ConnectorState state,
    Instant lastMessageAt,
    String instanceId,
    Instant connectorStartedAt,
    String lastMessageToken,
    ConnectorStateReason stateReason) {

  public RuntimeStatus {
    if (stateReason != null && state != ConnectorState.FAILED) {
      throw new IllegalArgumentException("A state reason only qualifies FAILED, not " + state);
    }
  }

  /** Without a state reason, for every state other than an explained FAILED. */
  public RuntimeStatus(
      UUID id,
      ConnectorState state,
      Instant lastMessageAt,
      String instanceId,
      Instant connectorStartedAt,
      String lastMessageToken) {
    this(id, state, lastMessageAt, instanceId, connectorStartedAt, lastMessageToken, null);
  }

  /**
   * Without a last-message token, for the many callers that report state rather than message
   * arrival. Kept so adding the token did not have to touch ~50 construction sites; the component
   * sits last for the same reason, even though it belongs beside {@code lastMessageAt}
   * semantically.
   */
  public RuntimeStatus(
      UUID id,
      ConnectorState state,
      Instant lastMessageAt,
      String instanceId,
      Instant connectorStartedAt) {
    this(id, state, lastMessageAt, instanceId, connectorStartedAt, null);
  }

  /**
   * connector_started_at is runtime observation metadata only, used for best-effort UI/refetch
   * timing and operator/debug visibility. It must not drive desired state, ownership, API
   * success/failure semantics, or command completion.
   */
  public RuntimeStatus(UUID id, ConnectorState state, Instant lastMessageAt, String instanceId) {
    this(id, state, lastMessageAt, instanceId, null, null);
  }

  /** Create a STOPPED status for data sources not currently loaded in connect. */
  public static RuntimeStatus stopped(UUID id) {
    return new RuntimeStatus(id, ConnectorState.STOPPED, null, null, null);
  }

  /** A FAILED status observed by the instance, explained by the reason. */
  public static RuntimeStatus failed(UUID id, String instanceId, ConnectorStateReason reason) {
    return new RuntimeStatus(id, ConnectorState.FAILED, null, instanceId, null, null, reason);
  }

  /** Create an UNKNOWN status when connect service is unreachable. */
  public static RuntimeStatus unknown(UUID id) {
    return new RuntimeStatus(id, ConnectorState.UNKNOWN, null, null, null);
  }
}
