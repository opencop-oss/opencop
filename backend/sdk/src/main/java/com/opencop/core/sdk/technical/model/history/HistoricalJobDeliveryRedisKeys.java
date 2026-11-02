// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.history;

import java.util.UUID;

/** Redis keys shared by processor and websocket for historical job delivery progress (Slice 14). */
public final class HistoricalJobDeliveryRedisKeys {

  private static final String PREFIX = "opencop:history:job-delivery";

  private HistoricalJobDeliveryRedisKeys() {}

  public static String stateHashKey(UUID jobId) {
    return PREFIX + ":" + jobId;
  }

  public static String deliveredCounterKey(UUID jobId) {
    return PREFIX + ":" + jobId + ":delivered";
  }

  public static String completionClaimKey(UUID jobId) {
    return PREFIX + ":" + jobId + ":completion-claimed";
  }

  public static String failureClaimKey(UUID jobId) {
    return PREFIX + ":" + jobId + ":failure-claimed";
  }
}
