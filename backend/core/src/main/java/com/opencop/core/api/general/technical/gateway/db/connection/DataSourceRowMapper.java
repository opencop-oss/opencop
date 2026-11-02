// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

class DataSourceRowMapper implements RowMapper<DataSource> {

  private final ObjectMapper objectMapper;

  DataSourceRowMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public DataSource mapRow(ResultSet result, int rowNum) throws SQLException {
    var provider = TransportKind.valueOf(result.getString("provider"));

    var id = result.getObject("id", UUID.class);
    var name = result.getString("name");
    var tenant = result.getString("tenant");
    var ingressMapperId = result.getObject("ingress_mapper_id", UUID.class);
    var integration = integrationReference(result);
    var enabled = result.getBoolean("enabled");
    var archivedAt = toInstant(result.getObject("archived_at", OffsetDateTime.class));
    var archivedBy = result.getObject("archived_by", UUID.class);
    var createdAt = toInstant(result.getObject("created_at", OffsetDateTime.class));
    var createdBy = result.getObject("created_by", UUID.class);
    var updatedAt = toInstant(result.getObject("updated_at", OffsetDateTime.class));
    var updatedBy = result.getObject("updated_by", UUID.class);
    var lastEventAt = toInstant(result.getObject("last_event_at", OffsetDateTime.class));
    var access = AccessMode.fromString(result.getString("access"));
    var defaultVisible = result.getBoolean("default_visible");

    return switch (provider) {
      case KAFKA -> {
        var specJson = result.getString("connection_spec");
        String topicName = null;
        String username = null;
        String brokerProvider = null;
        if (specJson != null) {
          var node = objectMapper.readTree(specJson);
          topicName = node.path("topicName").asText(null);
          username = node.path("username").asText(null);
          brokerProvider = node.path("brokerProvider").asText(null);
        }
        yield new KafkaDataSource(
            id,
            name,
            tenant,
            topicName,
            username,
            ingressMapperId,
            integration,
            access,
            enabled,
            archivedAt,
            archivedBy,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            lastEventAt,
            brokerProvider,
            defaultVisible);
      }
      case TCP, UDP, RTSP -> {
        ConnectionSpec spec = deserializeSpec(result.getString("connection_spec"));
        yield new SocketDataSource(
            id,
            name,
            tenant,
            spec,
            ingressMapperId,
            result.getObject("egress_mapper_id", UUID.class),
            integration,
            access,
            enabled,
            result.getString("assigned_instance"),
            archivedAt,
            archivedBy,
            createdAt,
            createdBy,
            updatedAt,
            updatedBy,
            lastEventAt,
            defaultVisible);
      }
    };
  }

  private static Instant toInstant(OffsetDateTime odt) {
    return odt != null ? odt.toInstant() : null;
  }

  private static IntegrationConnectionSchemaReference integrationReference(ResultSet result)
      throws SQLException {
    var integrationId = result.getObject("integration_id", UUID.class);
    if (integrationId == null) {
      return null;
    }
    return new IntegrationConnectionSchemaReference(
        integrationId, result.getObject("connection_schema_version", Integer.class));
  }

  private ConnectionSpec deserializeSpec(String json) throws SQLException {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, ConnectionSpec.class);
    } catch (Exception e) {
      throw new SQLException("Failed to deserialize connection spec JSON", e);
    }
  }
}
