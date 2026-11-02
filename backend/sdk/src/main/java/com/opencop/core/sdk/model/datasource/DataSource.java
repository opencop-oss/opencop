// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Data source metadata — sealed interface with two shapes:
 *
 * <ul>
 *   <li>{@link SocketDataSource} — TCP/UDP/RTSP connections with a ConnectionSpec and an assigned
 *       Connect instance.
 *   <li>{@link KafkaDataSource} — BYOD connections with topicName and username (from
 *       connection_spec JSONB). No host/port/spec — managed by Core's KafkaProvisioner, not
 *       Connect.
 * </ul>
 *
 * <p>The {@code enabled} field represents user intent: whether the connection should auto-start.
 * When a user stops a connection, enabled is set to false so it stays stopped across service
 * restarts.
 */
public sealed interface DataSource permits SocketDataSource, KafkaDataSource {
  UUID id();

  String name();

  String tenant();

  TransportKind provider();

  /**
   * The mapper decoding what the connection reads; runtime always uses its latest revision. Null
   * for a connection that only writes.
   */
  UUID ingressMapperId();

  IntegrationConnectionSchemaReference integration();

  AccessMode access();

  boolean enabled();

  Instant archivedAt();

  UUID archivedBy();

  Instant createdAt();

  UUID createdBy();

  Instant updatedAt();

  UUID updatedBy();

  Instant lastEventAt();

  boolean defaultVisible();
}
