// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("opencop.processor.startup-leader")
public class ProcessorStartupLeaderProperties {

  /** Redis key the single-replica startup leader contends on. */
  @NotBlank private String lockKey = ProcessorStartupLeader.DEFAULT_LOCK_KEY;

  /**
   * When false the Processor assumes leadership without contending, which is the supported
   * one-replica boundary.
   */
  private boolean enabled = false;

  public String getLockKey() {
    return lockKey;
  }

  public void setLockKey(String lockKey) {
    this.lockKey = lockKey;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
