// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/** Durable request and persistence limits for one Watchbox configuration. */
public final class WatchboxConfigurationLimits {

  public static final int MAX_NAME_CODE_POINTS = 255;
  public static final int MAX_DESCRIPTION_CODE_POINTS = 4_000;
  public static final int MAX_LAYER_IDS = 100;
  public static final int MAX_FILTER_PREDICATES = 20;

  private WatchboxConfigurationLimits() {}
}
