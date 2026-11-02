// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.connect;

import java.time.Instant;

/**
 * Connect worker registration and health metadata used for Postgres worker health.
 *
 * <p>Connect writes this shape to {@code profiles.connect_worker_run}. Core route-fresh routing
 * reads that table for ownership assignment and PUSH delivery targeting.
 *
 * <p>This class lives in SDK so both Connect (writer) and Core (reader) share the same definition.
 */
public record ConnectInstanceInfo(
    String instanceId,
    Instant startedAt,
    int connectionCount,
    String registrationId,
    long heartbeatSequence) {

  /** Whether this registration can participate in lifecycle route freshness checks. */
  public boolean hasRouteFreshnessFields() {
    return registrationId != null && !registrationId.isBlank() && heartbeatSequence >= 0;
  }
}
