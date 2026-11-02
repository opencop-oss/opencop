// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.rtsp;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.api.connect.technical.gateway.socket.RtspKlvConnector;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorFactory;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RtspKlvConnectorFactoryTest {

  private final RtspKlvConnectorFactory factory =
      new RtspKlvConnectorFactory(
          new RtspKlvExtractorFactory(RtspConnectorDefaults.defaults()),
          observability(),
          new ConnectProperties());

  @Test
  void createsRtspKlvConnector() {
    var spec = new ConnectionSpec(new RtspConfig("rtsp://localhost:8554/klv", null));

    assertThat(factory.create(UUID.randomUUID(), spec)).isInstanceOf(RtspKlvConnector.class);
  }

  private static RtspKlvObservability observability() {
    return new RtspKlvObservability(
        new SimpleMeterRegistry(), GlobalOpenTelemetry.getTracer(RtspKlvObservability.TRACER_NAME));
  }
}
