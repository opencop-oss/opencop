// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("opencop.watchbox.pipeline")
public class WatchboxPipelineProperties {

  @NotNull private Duration cacheTtl = Duration.ofSeconds(5);
  @NotNull private Duration futureTolerance = Duration.ofMinutes(5);

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public Duration getFutureTolerance() {
    return futureTolerance;
  }

  public void setFutureTolerance(Duration futureTolerance) {
    this.futureTolerance = futureTolerance;
  }

  @AssertTrue(
      message = "cacheTtl and futureTolerance must be positive, and cacheTtl at most one minute")
  public boolean isDurationsValid() {
    return positive(cacheTtl)
        && cacheTtl.compareTo(Duration.ofMinutes(1)) <= 0
        && positive(futureTolerance);
  }

  private static boolean positive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
