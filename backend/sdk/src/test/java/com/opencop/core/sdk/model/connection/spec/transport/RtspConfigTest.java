// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Map;
import org.geojson.Point;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RtspConfigTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void rtspWithoutExplicitPortDefaultsTo554() {
    var config = new RtspConfig("rtsp://example.com/klv", null);

    assertThat(config.getHost()).isEqualTo("example.com");
    assertThat(config.getPort()).isEqualTo(554);
  }

  @Test
  void rtspsWithoutExplicitPortDefaultsTo322() {
    var config = new RtspConfig("rtsps://example.com/klv", null);

    assertThat(config.getHost()).isEqualTo("example.com");
    assertThat(config.getPort()).isEqualTo(322);
  }

  @Test
  void explicitPortWinsForRtspAndRtsps() {
    assertThat(new RtspConfig("rtsp://example.com:8554/klv", null).getPort()).isEqualTo(8554);
    assertThat(new RtspConfig("rtsps://example.com:9443/klv", null).getPort()).isEqualTo(9443);
  }

  @Test
  void rejectsUserInfo() {
    assertThatThrownBy(() -> new RtspConfig("rtsp://user:pass@example.com/klv?token=secret", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userinfo");
  }

  @Test
  void rejectsNonRtspScheme() {
    assertThatThrownBy(() -> new RtspConfig("http://example.com/klv", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rtsp or rtsps");
  }

  @Test
  void acceptsQueryParametersForInternalRuntimeUrls() {
    var config = new RtspConfig("rtsp://example.com/klv?token=secret", null);

    assertThat(config.getHost()).isEqualTo("example.com");
    assertThat(config.getPort()).isEqualTo(554);
  }

  @Test
  void serializesConnectionSpecWithoutRuntimeDefaultsOrDerivedEndpoint() {
    var coordinates = new Point(-98.0000, 39.0000);
    var spec = new ConnectionSpec(new RtspConfig("rtsp://localhost:8554/klv", coordinates));

    var json = objectMapper.writeValueAsString(spec);
    var transport = transportMap(json);

    assertThat(transport)
        .containsEntry("type", "rtsp")
        .containsEntry("url", "rtsp://localhost:8554/klv")
        .containsKey("coordinates")
        .doesNotContainKeys("connectTimeoutMs", "readTimeoutMs", "preferTcp", "host", "port");
  }

  @Test
  void deserializesSerializedConnectionSpecWithRtspSourceConfig() {
    var spec = new ConnectionSpec(new RtspConfig("rtsp://localhost:8554/klv", null));

    var json = objectMapper.writeValueAsString(spec);
    var roundTrip = objectMapper.readValue(json, ConnectionSpec.class);

    assertThat(roundTrip.transport()).isInstanceOf(RtspConfig.class);
    var rtsp = (RtspConfig) roundTrip.transport();
    assertThat(rtsp.url()).isEqualTo("rtsp://localhost:8554/klv");
    assertThat(rtsp.coordinates()).isNull();
  }

  @Test
  void coreCreatedRtspSpecDeserializesWithRuntimeDefaults() {
    var json =
        """
        {
          "transport": {
            "type": "rtsp",
            "url": "rtsps://example.com/klv",
            "coordinates": null
          }
        }
        """;

    var roundTrip = objectMapper.readValue(json, ConnectionSpec.class);

    assertThat(roundTrip.transport()).isInstanceOf(RtspConfig.class);
    var rtsp = (RtspConfig) roundTrip.transport();
    assertThat(rtsp.url()).isEqualTo("rtsps://example.com/klv");
    assertThat(rtsp.getPort()).isEqualTo(322);
  }

  @Test
  void ignoresLegacyMetadataTimeoutDuringConnectionSpecDeserialization() {
    var json =
        """
        {
          "transport": {
            "type": "rtsp",
            "url": "rtsp://example.com/klv",
            "metadataTimeoutMs": 10000
          }
        }
        """;

    var roundTrip = objectMapper.readValue(json, ConnectionSpec.class);

    assertThat(roundTrip.transport()).isInstanceOf(RtspConfig.class);
    var rtsp = (RtspConfig) roundTrip.transport();
    assertThat(rtsp.url()).isEqualTo("rtsp://example.com/klv");
  }

  @Test
  void ignoresLegacyRuntimeDefaultsAndDerivedEndpointDuringConnectionSpecDeserialization() {
    var json =
        """
        {
          "transport": {
            "type": "rtsp",
            "url": "rtsp://example.com:8554/klv",
            "coordinates": null,
            "connectTimeoutMs": 1234,
            "readTimeoutMs": 5678,
            "preferTcp": false,
            "host": "legacy.example.invalid",
            "port": 9999
          }
        }
        """;

    var roundTrip = objectMapper.readValue(json, ConnectionSpec.class);

    assertThat(roundTrip.transport()).isInstanceOf(RtspConfig.class);
    var rtsp = (RtspConfig) roundTrip.transport();
    assertThat(rtsp.url()).isEqualTo("rtsp://example.com:8554/klv");
    assertThat(rtsp.getHost()).isEqualTo("example.com");
    assertThat(rtsp.getPort()).isEqualTo(8554);
  }

  @Test
  void rejectsUserInfoDuringConnectionSpecDeserialization() {
    var json =
        """
        {
          "transport": {
            "type": "rtsp",
            "url": "rtsp://user:pass@example.com/klv"
          }
        }
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, ConnectionSpec.class))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("userinfo");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> transportMap(String json) {
    Map<String, Object> spec = objectMapper.readValue(json, Map.class);
    return (Map<String, Object>) spec.get("transport");
  }
}
