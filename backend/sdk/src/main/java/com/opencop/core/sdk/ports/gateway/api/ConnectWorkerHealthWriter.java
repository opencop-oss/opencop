// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import java.time.Duration;
import java.util.UUID;

/** Writes this process run's Connect worker health. */
public interface ConnectWorkerHealthWriter {

  void writeHeartbeat(UUID workerRunId, ConnectInstanceInfo info, Duration heartbeatTtl);

  void markStopped(UUID workerRunId);
}
