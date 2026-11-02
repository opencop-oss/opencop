// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox reset-confirmation reason. The enum constant name is the JSON wire value;
 * keep released names stable and add new constants for new reset causes.
 */
public enum WatchboxResetReason {
  POLYGON_CHANGED,
  LAYER_REMOVED,
  REENABLED
}
