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
@ConfigurationProperties("opencop.watchbox.alerts")
public class WatchboxAlertProperties {

  @Valid @NotNull private Drain drain = new Drain();

  public Drain getDrain() {
    return drain;
  }

  public void setDrain(Drain drain) {
    this.drain = drain;
  }

  public static class Drain {

    @Min(1)
    private int batchSize = 128;

    @NotNull private Duration pollInterval = Duration.ofSeconds(5);

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public Duration getPollInterval() {
      return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
      this.pollInterval = pollInterval;
    }

    @AssertTrue(message = "pollInterval must be positive")
    public boolean isPollIntervalPositive() {
      return pollInterval != null && !pollInterval.isZero() && !pollInterval.isNegative();
    }
  }
}
