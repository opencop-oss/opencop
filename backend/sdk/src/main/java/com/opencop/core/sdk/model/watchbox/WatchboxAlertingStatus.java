// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox alerting state. The enum constant name is the JSON wire value; keep released
 * names stable and add new constants for new states.
 */
public enum WatchboxAlertingStatus {
  ACTIVE,
  DEGRADED,
  FAILED,
  DISABLED
}
