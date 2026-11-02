// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat;
import java.time.Duration;

/** Processor-side writer for the watchbox-detection liveness heartbeat row. */
public interface WatchboxDetectionHeartbeatWriter {

  /** Upserts this instance's heartbeat row with a fresh expiry {@code ttl} from now. */
  void writeHeartbeat(WatchboxDetectionHeartbeat heartbeat, Duration ttl);

  /**
   * Marks this instance's row stopped and expired, so status turns amber without waiting out the
   * TTL.
   */
  void markStopped(String instanceId);
}
