// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.processor.ports.gateway.db.DataSourceTransportGateway;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public class DataSourceTransportReader implements DataSourceTransportGateway {

  private static final Logger log = LoggerFactory.getLogger(DataSourceTransportReader.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public DataSourceTransportReader(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  /** Returns the RTSP URL for the given source, or empty if the source is not RTSP. */
  @Override
  public Optional<String> getRtspUrl(UUID sourceId) {
    var rows =
        jdbc.query(
            "SELECT provider, connection_spec FROM profiles.data_source WHERE id = :id AND archived_at IS NULL",
            Map.of("id", sourceId),
            (rs, _) -> Map.entry(rs.getString("provider"), rs.getString("connection_spec")));

    if (rows.isEmpty()) {
      return Optional.empty();
    }

    var row = rows.getFirst();
    if (!TransportKind.RTSP.name().equalsIgnoreCase(row.getKey())) {
      return Optional.empty();
    }

    try {
      var spec = objectMapper.readValue(row.getValue(), ConnectionSpec.class);
      if (spec.transport() instanceof RtspConfig rtsp) {
        return Optional.of(rtsp.url());
      }
    } catch (Exception e) {
      log.warn("Failed to parse connection spec for source {}: {}", sourceId, e.getMessage());
    }
    return Optional.empty();
  }
}
