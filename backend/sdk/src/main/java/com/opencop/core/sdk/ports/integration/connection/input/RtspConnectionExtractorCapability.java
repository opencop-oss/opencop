// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspConnectionMaterial;

/**
 * An RTSP extractor: it reads a connection's input and produces RTSP connection material.
 *
 * <p>The registry requires it of every RTSP extractor, so a lookup typed by {@link
 * ExtractorProtocol#RTSP} returns an extractor that produces this material.
 */
public interface RtspConnectionExtractorCapability
    extends ConnectionExtractorCapability<ConnectionInput, RtspConnectionMaterial> {

  @Override
  default TransportProtocol protocol() {
    return TransportProtocol.RTSP;
  }
}
