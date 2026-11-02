// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.factory.connection;

import com.opencop.core.sdk.business.model.integration.connection.material.rtsp.RtspEndpointMaterial;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;

/** Assembles the connection spec of a new RTSP connection from its already-decided parts. */
public final class RtspConnectionSpecFactory {

  private RtspConnectionSpecFactory() {}

  public static ConnectionSpec create(RtspEndpointMaterial endpoint) {
    return new ConnectionSpec(new RtspConfig(endpoint.url(), endpoint.coordinates()));
  }
}
