// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.rtsp;

import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.socket.RtspKlvConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.api.connect.technical.gateway.socket.rtsp.KlvPacketFramer;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorFactory;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class RtspKlvConnectorFactory implements ConnectorFactory {

  private final RtspKlvExtractorFactory extractorFactory;
  private final RtspKlvObservability observability;
  private final Duration firstMetadataTimeout;

  RtspKlvConnectorFactory(
      RtspKlvExtractorFactory extractorFactory,
      RtspKlvObservability observability,
      ConnectProperties connectProperties) {
    this.extractorFactory = extractorFactory;
    this.observability = observability;
    this.firstMetadataTimeout = connectProperties.getRtspKlv().getFirstMetadataTimeout();
  }

  @Override
  public SocketConnector create(UUID sourceId, ConnectionSpec spec) {
    return new RtspKlvConnector(
        sourceId,
        (RtspConfig) spec.transport(),
        extractorFactory,
        new KlvPacketFramer(),
        firstMetadataTimeout,
        observability);
  }
}
