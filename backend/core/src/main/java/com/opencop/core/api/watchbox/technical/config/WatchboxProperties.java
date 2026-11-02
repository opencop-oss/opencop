// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "opencop.watchbox")
public class WatchboxProperties {

  private int maxPolygonVertices = 2_000;

  // How long a detection-heartbeat retained-retry episode may run before the status rollup
  // degrades affected watchboxes with WATCHBOX_DETECTION_PARTIALLY_STALLED. Below this bound a
  // wedge is expected turbulence (generation churn, a database blip); past it, alerts from the
  // wedged partition have credibly stopped flowing.
  @NotNull private Duration detectionDegradeThreshold = Duration.ofSeconds(60);

  public int maxPolygonVertices() {
    return maxPolygonVertices;
  }

  public void setMaxPolygonVertices(int maxPolygonVertices) {
    this.maxPolygonVertices = maxPolygonVertices;
  }

  public Duration detectionDegradeThreshold() {
    return detectionDegradeThreshold;
  }

  public void setDetectionDegradeThreshold(Duration detectionDegradeThreshold) {
    this.detectionDegradeThreshold = detectionDegradeThreshold;
  }

  @AssertTrue(message = "detectionDegradeThreshold must be positive")
  public boolean isDetectionDegradeThresholdPositive() {
    return detectionDegradeThreshold != null
        && !detectionDegradeThreshold.isZero()
        && !detectionDegradeThreshold.isNegative();
  }
}
