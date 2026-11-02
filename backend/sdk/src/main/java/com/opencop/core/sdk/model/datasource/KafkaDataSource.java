// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.time.Instant;
import java.util.UUID;

/**
 * BYOD/Kafka data source — has topicName and username (deserialized from connection_spec JSONB). No
 * host/port/ConnectionSpec — managed by Core's KafkaProvisioner, not Connect.
 */
public record KafkaDataSource(
    UUID id,
    String name,
    String tenant,
    String topicName,
    String username,
    UUID ingressMapperId,
    IntegrationConnectionSchemaReference integration,
    AccessMode access,
    boolean enabled,
    Instant archivedAt,
    UUID archivedBy,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt,
    String brokerProvider,
    boolean defaultVisible)
    implements DataSource {

  @Override
  public TransportKind provider() {
    return TransportKind.KAFKA;
  }
}
