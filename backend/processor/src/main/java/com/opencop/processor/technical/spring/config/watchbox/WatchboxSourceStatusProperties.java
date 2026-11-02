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
@ConfigurationProperties("opencop.watchbox.source-status")
public class WatchboxSourceStatusProperties {

  @Valid @NotNull private Gc gc = new Gc();

  public Gc getGc() {
    return gc;
  }

  public void setGc(Gc gc) {
    this.gc = gc;
  }

  public static class Gc {
    private boolean enabled = true;
    @NotNull private Duration interval = Duration.ofHours(1);
    @NotNull private Duration initialDelay = Duration.ofMinutes(15);

    @Min(1)
    private int batchSize = 5_000;

    @Min(1)
    private int maxBatchesPerTick = 20;

    // Age guard on epoch-stranded rows only — current-epoch rows are never deleted — so this is
    // purely the forensic window: how long a superseded generation's degraded-status history stays
    // answerable by SQL after an epoch bump. 72h matches detection retention for one uniform
    // watchbox-history window.
    @NotNull private Duration retention = Duration.ofHours(72);

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

    public Duration getRetention() {
      return retention;
    }

    public void setRetention(Duration retention) {
      this.retention = retention;
    }

    @AssertTrue(message = "GC interval and initialDelay must be positive")
    public boolean isDurationsPositive() {
      return positive(interval) && positive(initialDelay);
    }

    @AssertTrue(message = "retention must be positive")
    public boolean isRetentionPositive() {
      return positive(retention);
    }
  }

  private static boolean positive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }
}
