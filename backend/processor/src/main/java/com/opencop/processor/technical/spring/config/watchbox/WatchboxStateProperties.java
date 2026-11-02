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
@ConfigurationProperties("opencop.watchbox.state")
public class WatchboxStateProperties {

  @NotNull private Duration liveWindow = Duration.ofMinutes(10);
  @NotNull private Duration retention = Duration.ofHours(1);
  @Valid @NotNull private Gc gc = new Gc();
  @Valid @NotNull private Breaker breaker = new Breaker();

  public Duration getLiveWindow() {
    return liveWindow;
  }

  public void setLiveWindow(Duration liveWindow) {
    this.liveWindow = liveWindow;
  }

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

  public Breaker getBreaker() {
    return breaker;
  }

  public void setBreaker(Breaker breaker) {
    this.breaker = breaker;
  }

  @AssertTrue(message = "retention must be at least liveWindow")
  public boolean isRetentionAtLeastLiveWindow() {
    if (retention == null || liveWindow == null) {
      return true;
    }
    return retention.compareTo(liveWindow) >= 0;
  }

  @AssertTrue(message = "liveWindow and retention must be positive")
  public boolean isWindowsPositive() {
    return positive(liveWindow) && positive(retention);
  }

  private static boolean positive(Duration duration) {
    return duration != null && !duration.isZero() && !duration.isNegative();
  }

  public static class Gc {
    private boolean enabled = true;
    @NotNull private Duration interval = Duration.ofHours(1);
    @NotNull private Duration initialDelay = Duration.ofMinutes(5);

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

  public static class Breaker {
    private boolean enabled = true;

    @Min(1)
    private long cap = 10_000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getCap() {
      return cap;
    }

    public void setCap(long cap) {
      this.cap = cap;
    }
  }
}
