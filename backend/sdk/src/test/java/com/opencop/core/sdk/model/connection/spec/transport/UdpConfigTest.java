// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UdpConfigTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void listenPortPrefersLocalPortWhenSet() {
    var config = new UdpConfig("127.0.0.1", 9000, 9100);

    assertThat(config.listenPort()).isEqualTo(9100);
    assertThat(config.getPort()).isEqualTo(9000);
  }

  @Test
  void listenPortFallsBackToPortWhenLocalPortIsNull() {
    var config = new UdpConfig("127.0.0.1", 9000, null);

    assertThat(config.listenPort()).isEqualTo(9000);
  }

  @Test
  void connectionSpecWithNullLocalPortDeserializes() {
    var json =
        """
        {
          "transport": {
            "type": "udp",
            "host": "127.0.0.1",
            "port": 9000,
            "localPort": null
          }
        }
        """;

    var spec = objectMapper.readValue(json, ConnectionSpec.class);

    assertThat(spec.transport()).isInstanceOf(UdpConfig.class);
    var udp = (UdpConfig) spec.transport();
    assertThat(udp.localPort()).isNull();
    assertThat(udp.listenPort()).isEqualTo(9000);
  }

  @Test
  void connectionSpecRoundTripsWithLocalPortPreserved() {
    var spec = new ConnectionSpec(new UdpConfig("127.0.0.1", 9000, 9100));

    var roundTrip =
        objectMapper.readValue(objectMapper.writeValueAsString(spec), ConnectionSpec.class);

    assertThat(roundTrip).isEqualTo(spec);
    assertThat(((UdpConfig) roundTrip.transport()).listenPort()).isEqualTo(9100);
  }
}
