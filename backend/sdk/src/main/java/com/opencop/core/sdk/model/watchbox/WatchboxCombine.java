// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox filter combiner. The enum constant name is the JSON wire value; keep released
 * names stable and add new constants for new operations.
 *
 * <p>ALL requires every predicate to match. ANY requires at least one predicate to match.
 */
public enum WatchboxCombine {
  ALL,
  ANY
}
