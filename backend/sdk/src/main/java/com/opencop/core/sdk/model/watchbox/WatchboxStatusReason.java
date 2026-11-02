// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox status reason. The enum constant name is the JSON wire value; keep released
 * names stable and add new constants for new reasons.
 */
public enum WatchboxStatusReason {
  WATCHBOX_DISABLED,
  WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED,
  WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
  WATCHBOX_OBJECT_ID_CHURN_DETECTED,
  // Read-time-only: computed from the detection heartbeat during status assembly, never persisted
  // to watchbox_source_status (whose reason_code CHECK deliberately excludes it).
  WATCHBOX_DETECTION_NOT_RUNNING,
  /**
   * Read-time-only, like {@link #WATCHBOX_DETECTION_NOT_RUNNING}: computed from the detection
   * heartbeat during status assembly, never persisted to watchbox_source_status. At least one
   * partition of the watchbox listener has been wedged in retained retry-in-place beyond the
   * configured degrade threshold - detection is alive, but alerts from the wedged partition(s) have
   * stopped flowing. Caused by generation churn or an infrastructure outage on that partition's
   * batch. To investigate: the heartbeat row's {@code retained_retry_*} columns
   * (watchbox.watchbox_detection_heartbeat), the {@code
   * opencop.watchbox.detection.retained.retry.partitions} gauges, and the Processor's watchbox
   * retained-retry log stream.
   */
  WATCHBOX_DETECTION_PARTIALLY_STALLED
}
