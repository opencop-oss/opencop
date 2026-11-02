// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import java.time.Duration;

/** Core-side read of watchbox-detection liveness. */
public interface WatchboxDetectionLivenessReader {

  /**
   * Liveness rollup over every instance's detection heartbeat row. Alive means some Processor
   * instance holds a fresh, non-stopped heartbeat with at least one assigned partition; an idle
   * poll proves the loop turns but not that it consumes anything - a consumer with zero assignment
   * polls happily forever - so assignment is part of alive. On top of alive, a fresh row whose
   * retained-retry episode started more than {@code stalledThreshold} ago reads as partially
   * stalled: the listener is alive but at least one of its partitions has stopped advancing.
   * Multi-instance semantics are any-fresh-row: any alive row makes the answer alive, and any fresh
   * row wedged past the threshold makes it stalled. Callers must fail closed: if this read throws,
   * the answer is {@link DetectionLiveness#NOT_RUNNING}, never healthy.
   */
  DetectionLiveness detectionLiveness(Duration stalledThreshold);

  /**
   * Detection liveness as the status rollup consumes it. {@link #PARTIALLY_STALLED} implies alive:
   * stall facts are only read from fresh rows, because a row that expired outright already carries
   * the stronger {@link #NOT_RUNNING} signal.
   */
  enum DetectionLiveness {
    NOT_RUNNING,
    PARTIALLY_STALLED,
    RUNNING
  }
}
