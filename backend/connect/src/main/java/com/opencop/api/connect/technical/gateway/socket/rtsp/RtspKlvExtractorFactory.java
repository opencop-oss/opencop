// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.rtsp;

import com.opencop.api.connect.technical.config.RtspConnectorDefaults;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class RtspKlvExtractorFactory {

  private final RtspConnectorDefaults connectorDefaults;

  public RtspKlvExtractorFactory(RtspConnectorDefaults connectorDefaults) {
    this.connectorDefaults = Objects.requireNonNull(connectorDefaults, "connectorDefaults");
  }

  public RtspKlvExtractor create(RtspConfig config) {
    return new JavaCvRtspKlvExtractor(config, connectorDefaults);
  }
}
