// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("opencop.watchbox.detection")
public class WatchboxDetectionProperties {

  @NotNull private Duration retention = Duration.ofHours(72);
  @Valid @NotNull private Gc gc = new Gc();

  public Duration getRetention() {
    return retention;
  }

  public void setRetention(Duration retention) {
    this.retention = retention;
  }

  public Gc getGc() {
    return gc;
  }

  public void setGc(Gc gc) {
    this.gc = gc;
  }

  @AssertTrue(message = "retention must be positive")
  public boolean isRetentionPositive() {
    return positive(retention);
  }

  private static boolean positive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }

  public static class Gc {
    private boolean enabled = true;
    @NotNull private Duration interval = Duration.ofHours(1);
    @NotNull private Duration initialDelay = Duration.ofMinutes(10);

    @Min(1)
    private int batchSize = 5_000;

    @Min(1)
    private int maxBatchesPerTick = 20;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getInterval() {
      return interval;
    }

    public void setInterval(Duration interval) {
      this.interval = interval;
    }

    public Duration getInitialDelay() {
      return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
      this.initialDelay = initialDelay;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public int getMaxBatchesPerTick() {
      return maxBatchesPerTick;
    }

    public void setMaxBatchesPerTick(int maxBatchesPerTick) {
      this.maxBatchesPerTick = maxBatchesPerTick;
    }

    @AssertTrue(message = "GC interval and initialDelay must be positive")
    public boolean isDurationsPositive() {
      return positive(interval) && positive(initialDelay);
    }
  }
}
