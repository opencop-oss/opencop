// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.TcpConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.material.tcp.TcpConnectionMaterial;

/** An extractor that turns a TCP connection's input into its material. */
public interface TcpConnectionExtractorCapability
    extends ConnectionExtractorCapability<TcpConnectionInput, TcpConnectionMaterial> {

  @Override
  default TransportProtocol protocol() {
    return TransportProtocol.TCP;
  }
}
