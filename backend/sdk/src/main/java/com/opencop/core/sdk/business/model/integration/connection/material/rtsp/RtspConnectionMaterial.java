// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.rtsp;

import java.util.Objects;

/** Complete RTSP endpoint and the security material produced before connection creation. */
public record RtspConnectionMaterial(RtspEndpointMaterial endpoint, RtspSecurityMaterial security) {

  public RtspConnectionMaterial {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(security, "security");
  }

  public static RtspConnectionMaterial plain(RtspEndpointMaterial endpoint) {
    return new RtspConnectionMaterial(endpoint, new NoSecurityMaterial());
  }
}
