// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

/**
 * Transport protocol an integration supports for connection setup.
 *
 * <p>This is the provider-facing declaration a plugin makes about how its connections are
 * established. It is deliberately distinct from the Core-side connector kinds carried by the legacy
 * connection request model.
 */
public enum TransportProtocol {
  /** A TCP socket, framed into messages by a delimiter or by an integration's framing. */
  TCP,

  /** A UDP listener on a host and port. */
  UDP,

  /** An RTSP stream, whose KLV metadata the KLV mapper decodes. */
  RTSP,

  /** A Kafka topic and producer credentials the host provisions on the broker. */
  KAFKA
}
