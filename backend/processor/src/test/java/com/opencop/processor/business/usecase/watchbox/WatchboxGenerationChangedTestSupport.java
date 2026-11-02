// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.watchbox;

/** Test-only construction boundary for the listener-visible generation-change signal. */
public final class WatchboxGenerationChangedTestSupport {

  private WatchboxGenerationChangedTestSupport() {}

  public static WatchboxGenerationChangedException generationChanged() {
    return new WatchboxGenerationChangedException();
  }
}
