// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataSourceRowMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DataSourceRowMapper mapper = new DataSourceRowMapper(objectMapper);

  @Test
  void mapsRtspRowsAsSocketDataSources() throws Exception {
    var spec = new ConnectionSpec(new RtspConfig("rtsp://localhost:8554/klv", null));
    var result = rtspResult(objectMapper.writeValueAsString(spec));

    var dataSource = mapper.mapRow(result, 0);

    assertThat(dataSource).isInstanceOf(SocketDataSource.class);
    var socket = (SocketDataSource) dataSource;
    assertThat(socket.provider()).isEqualTo(TransportKind.RTSP);
    assertThat(socket.spec().transport()).isInstanceOf(RtspConfig.class);
    assertThat(socket.assignedInstance()).isEqualTo("connect-a");
  }

  @Test
  void mapsStoredRtspRowsWithLegacyHeartbeatProperty() throws Exception {
    var result =
        rtspResult(
            """
            {
              "transport": {
                "type": "rtsp",
                "url": "rtsp://localhost:8554/klv",
                "coordinates": null
              }
            }
            """);

    var dataSource = mapper.mapRow(result, 0);

    var socket = (SocketDataSource) dataSource;
    var rtsp = (RtspConfig) socket.spec().transport();
    assertThat(rtsp.url()).isEqualTo("rtsp://localhost:8554/klv");
    assertThat(rtsp.coordinates()).isNull();
  }

  @Test
  void mapsLegacyRtspRowsWhileIgnoringPersistedRuntimeDefaults() throws Exception {
    var result =
        rtspResult(
            """
            {
              "transport": {
                "type": "rtsp",
                "url": "rtsp://localhost:8554/klv",
                "coordinates": null,
                "connectTimeoutMs": 1234,
                "readTimeoutMs": 5678,
                "preferTcp": false,
                "host": "legacy.example.invalid",
                "port": 9999
              }
            }
            """);

    var dataSource = mapper.mapRow(result, 0);

    var socket = (SocketDataSource) dataSource;
    var rtsp = (RtspConfig) socket.spec().transport();
    assertThat(rtsp.getHost()).isEqualTo("localhost");
    assertThat(rtsp.getPort()).isEqualTo(8554);
  }

  @Test
  void rejectsRtspUserInfoDuringRowMapping() throws Exception {
    var result =
        rtspResult(
            """
            {
              "transport": {
                "type": "rtsp",
                "url": "rtsp://user:pass@localhost:8554/klv"
              }
            }
            """);

    assertThatThrownBy(() -> mapper.mapRow(result, 0))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Failed to deserialize connection spec JSON")
        .hasStackTraceContaining("userinfo");
  }

  private ResultSet rtspResult(String specJson) throws Exception {
    var sourceId = UUID.randomUUID();
    var createdAt = OffsetDateTime.parse("2026-05-21T12:00:00Z");
    var updatedAt = OffsetDateTime.parse("2026-05-21T12:01:00Z");
    var result = mock(ResultSet.class);
    when(result.getString("provider")).thenReturn("RTSP");
    when(result.getObject("id", UUID.class)).thenReturn(sourceId);
    when(result.getString("name")).thenReturn("rtsp-source");
    when(result.getString("tenant")).thenReturn("default");
    when(result.getBoolean("enabled")).thenReturn(true);
    when(result.getObject("created_at", OffsetDateTime.class)).thenReturn(createdAt);
    when(result.getObject("updated_at", OffsetDateTime.class)).thenReturn(updatedAt);
    when(result.getString("access")).thenReturn("R");
    when(result.getString("connection_spec")).thenReturn(specJson);
    when(result.getString("assigned_instance")).thenReturn("connect-a");
    return result;
  }
}
