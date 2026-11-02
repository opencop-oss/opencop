// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * Constant names are persisted verbatim into {@code watchbox_detection.transition} and pinned by
 * V37's CHECK constraint — renaming or adding a value requires a migration. The same names are the
 * JSON wire values of {@link WatchboxAlertEvent#kind()}.
 */
public enum WatchboxTransitionKind {
  ENTER,
  LEAVE,
  FILTER_MATCH_GAINED,
  FILTER_MATCH_LOST
}
