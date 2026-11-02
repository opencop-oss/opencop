// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("opencop.realtime-mapping-outcomes")
public class RealtimeMappingOutcomeProperties {

  private boolean enabled = true;

  @Min(1)
  @Max(100)
  private int maxProblemResourceTags = 100;

  @NotNull private Duration warningSummaryInterval = Duration.ofMinutes(5);

  @Min(1)
  private int warningStateMax = 1_000;

  @Min(1)
  @Max(100)
  private int warningGlobalMaxPerInterval = 100;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMaxProblemResourceTags() {
    return maxProblemResourceTags;
  }

  public void setMaxProblemResourceTags(int maxProblemResourceTags) {
    this.maxProblemResourceTags = maxProblemResourceTags;
  }

  public Duration getWarningSummaryInterval() {
    return warningSummaryInterval;
  }

  public void setWarningSummaryInterval(Duration warningSummaryInterval) {
    this.warningSummaryInterval = warningSummaryInterval;
  }

  public int getWarningStateMax() {
    return warningStateMax;
  }

  public void setWarningStateMax(int warningStateMax) {
    this.warningStateMax = warningStateMax;
  }

  public int getWarningGlobalMaxPerInterval() {
    return warningGlobalMaxPerInterval;
  }

  public void setWarningGlobalMaxPerInterval(int warningGlobalMaxPerInterval) {
    this.warningGlobalMaxPerInterval = warningGlobalMaxPerInterval;
  }

  @AssertTrue(message = "warningSummaryInterval must be greater than zero")
  public boolean isWarningSummaryIntervalPositive() {
    return warningSummaryInterval != null
        && !warningSummaryInterval.isZero()
        && !warningSummaryInterval.isNegative();
  }
}
