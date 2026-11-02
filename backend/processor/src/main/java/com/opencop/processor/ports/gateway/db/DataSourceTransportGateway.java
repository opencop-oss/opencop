// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.gateway.db;

import java.util.Optional;
import java.util.UUID;

public interface DataSourceTransportGateway {

  /** Returns the RTSP stream URL for the given source, or empty if the source is not RTSP. */
  Optional<String> getRtspUrl(UUID sourceId);
}
