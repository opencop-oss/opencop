// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import java.util.concurrent.atomic.AtomicReference;

/** Coordinates Core startup work that can otherwise race assignment reconciliation. */
public class DataSourceStartupCoordinator {

  public enum AutoStartStatus {
    PENDING,
    RUNNING,
    SKIPPED,
    COMPLETED,
    FAILED
  }

  private final AtomicReference<AutoStartStatus> autoStartStatus =
      new AtomicReference<>(AutoStartStatus.PENDING);

  public void autoStartStarted() {
    autoStartStatus.set(AutoStartStatus.RUNNING);
  }

  public void autoStartSkipped() {
    autoStartStatus.set(AutoStartStatus.SKIPPED);
  }

  public void autoStartCompleted() {
    autoStartStatus.set(AutoStartStatus.COMPLETED);
  }

  public void autoStartFailed() {
    autoStartStatus.set(AutoStartStatus.FAILED);
  }

  public AutoStartStatus autoStartStatus() {
    return autoStartStatus.get();
  }

  public boolean isAutoStartResolved() {
    return switch (autoStartStatus.get()) {
      case SKIPPED, COMPLETED, FAILED -> true;
      case PENDING, RUNNING -> false;
    };
  }
}
