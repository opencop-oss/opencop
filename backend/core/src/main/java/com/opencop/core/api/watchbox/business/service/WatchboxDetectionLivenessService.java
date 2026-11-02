// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader;
import com.opencop.core.sdk.ports.gateway.api.WatchboxDetectionLivenessReader.DetectionLiveness;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request-scoped answer to "is watchbox detection actually running anywhere, and is it advancing
 * everywhere?", backed by the Processor's TTL heartbeat row and its retained-retry facts. Fail
 * closed: if the heartbeat cannot be read, the answer is not-running, never healthy - absence of
 * evidence must not present as good news.
 */
public class WatchboxDetectionLivenessService {

  private static final Logger log = LoggerFactory.getLogger(WatchboxDetectionLivenessService.class);

  private final WatchboxDetectionLivenessReader reader;
  private final Duration degradeThreshold;

  public WatchboxDetectionLivenessService(
      WatchboxDetectionLivenessReader reader, Duration degradeThreshold) {
    this.reader = Objects.requireNonNull(reader, "reader");
    this.degradeThreshold = Objects.requireNonNull(degradeThreshold, "degradeThreshold");
  }

  public DetectionLiveness detectionLiveness() {
    try {
      return reader.detectionLiveness(degradeThreshold);
    } catch (Exception e) {
      // Operator-visible: without this line, a Core-side read failure is indistinguishable in
      // the logs from a genuinely stopped Processor - both surface only as DEGRADED status.
      log.warn(
          "WATCHBOX_DETECTION_LIVENESS_READ_FAILED reason=POSTGRES_READ_FAILED exception_class={}",
          e.getClass().getSimpleName());
      log.debug("WATCHBOX_DETECTION_LIVENESS_READ_FAILED detail=stack_trace", e);
      return DetectionLiveness.NOT_RUNNING;
    }
  }
}
