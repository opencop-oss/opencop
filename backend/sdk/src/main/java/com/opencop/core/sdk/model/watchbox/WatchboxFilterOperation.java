// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox filter operation. The enum constant name is the JSON wire value; keep
 * released names stable and add new constants for new operations.
 *
 * <p>EXISTS matches a present non-null property. EQUALS matches a string property against one
 * value. IN matches a string property against any configured value. SUBSTRING_IN matches a string
 * property's 0-based substring window against any configured value.
 */
public enum WatchboxFilterOperation {
  EXISTS,
  EQUALS,
  IN,
  SUBSTRING_IN
}
