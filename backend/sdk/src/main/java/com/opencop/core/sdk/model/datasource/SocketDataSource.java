// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Socket-based data source (TCP, UDP, RTSP) — its {@link ConnectionSpec} describes the whole
 * connection, and it has an assigned Connect instance.
 *
 * <p>The transport kind, endpoint, and listen port are all read from {@link #spec()}; nothing here
 * restates them.
 *
 * <p>The {@code assignedInstance} field indicates which Connect instance owns this connection
 * (e.g., "connect-1"). NULL means unassigned — will be assigned on first start.
 *
 * <p>The {@code egressMapperId} field names the mapper encoding what the connection writes. It is
 * null for a connection that only reads; which access a transport allows is decided before a source
 * is built.
 */
public record SocketDataSource(
    UUID id,
    String name,
    String tenant,
    ConnectionSpec spec,
    UUID ingressMapperId,
    UUID egressMapperId,
    IntegrationConnectionSchemaReference integration,
    AccessMode access,
    boolean enabled,
    String assignedInstance,
    Instant archivedAt,
    UUID archivedBy,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt,
    boolean defaultVisible)
    implements DataSource {

  public SocketDataSource {
    Objects.requireNonNull(spec, "spec");
  }

  @Override
  public TransportKind provider() {
    return spec.transport().kind();
  }

  /**
   * Builds a new SocketDataSource. Lifecycle, audit, and assignment fields start unset; the
   * connection decodes with its own ingress mapper and encodes with its own egress mapper.
   */
  public static SocketDataSource fromSpec(
      UUID sourceId,
      String name,
      UUID ingressMapperId,
      UUID egressMapperId,
      String tenant,
      ConnectionSpec spec,
      IntegrationConnectionSchemaReference integration,
      AccessMode access,
      boolean defaultVisible,
      UUID createdBy) {
    return new SocketDataSource(
        sourceId,
        name,
        tenant,
        spec,
        ingressMapperId,
        egressMapperId,
        integration,
        access,
        true, // enabled by default
        null, // assignedInstance - set by setAssignedInstance()
        null, // archivedAt - null while active
        null, // archivedBy - null while active
        null, // createdAt - set by DB default
        createdBy,
        null, // updatedAt - set by DB trigger
        null, // updatedBy - null on creation
        null, // lastEventAt - null until first event
        defaultVisible);
  }
}
