// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connection.spec.transport;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TcpConfig.class, name = "tcp"),
  @JsonSubTypes.Type(value = UdpConfig.class, name = "udp"),
  @JsonSubTypes.Type(value = RtspConfig.class, name = "rtsp")
})
public sealed interface TransportConfig permits TcpConfig, UdpConfig, RtspConfig {
  /** The transport kind this configuration describes. */
  TransportKind kind();

  String getHost();

  int getPort();

  /**
   * The refs of every stored secret this transport reads, which the connection owns. A transport
   * that gains a secret must name it here, or archiving and rotating the connection leave it
   * behind.
   */
  List<String> secretRefs();
}
