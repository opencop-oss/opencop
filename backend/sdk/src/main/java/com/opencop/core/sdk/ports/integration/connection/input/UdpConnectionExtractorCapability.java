// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.udp.UdpConnectionMaterial;

/**
 * A UDP extractor: it reads a connection's input and produces UDP connection material.
 *
 * <p>The registry requires it of every UDP extractor, so a lookup typed by {@link
 * ExtractorProtocol#UDP} returns an extractor that produces this material.
 */
public interface UdpConnectionExtractorCapability
    extends ConnectionExtractorCapability<ConnectionInput, UdpConnectionMaterial> {

  @Override
  default TransportProtocol protocol() {
    return TransportProtocol.UDP;
  }
}
