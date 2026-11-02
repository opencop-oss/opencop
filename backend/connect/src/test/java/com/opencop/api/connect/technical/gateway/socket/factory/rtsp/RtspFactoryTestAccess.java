// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket.factory.rtsp;

import com.opencop.api.connect.technical.config.ConnectProperties;
import com.opencop.api.connect.technical.gateway.socket.rtsp.RtspKlvExtractorFactory;
import com.opencop.api.connect.technical.observability.rtsp.RtspKlvObservability;

/** Test-only access to the package-private RTSP connector factory constructor. */
public final class RtspFactoryTestAccess {

  private RtspFactoryTestAccess() {}

  public static RtspKlvConnectorFactory rtspKlvFactory(
      RtspKlvExtractorFactory extractorFactory, RtspKlvObservability observability) {
    return new RtspKlvConnectorFactory(extractorFactory, observability, new ConnectProperties());
  }
}
