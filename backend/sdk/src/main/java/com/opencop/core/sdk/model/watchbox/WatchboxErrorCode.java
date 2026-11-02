// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

/**
 * FE-visible Watchbox action error code. The enum constant name is the JSON wire value; keep
 * released names stable and add new constants for new failures.
 */
public enum WatchboxErrorCode {
  WATCHBOX_INVALID_REQUEST,
  WATCHBOX_INVALID_POLYGON,
  WATCHBOX_INVALID_FILTER,
  WATCHBOX_INVALID_NAME,
  WATCHBOX_LAYER_REQUIRED,
  WATCHBOX_MANAGEMENT_ROLE_REQUIRED,
  WATCHBOX_NO_ELIGIBLE_SOURCES,
  WATCHBOX_MAPPER_UNAVAILABLE,
  WATCHBOX_MAPPER_OBJECT_ID_NOT_CONFIGURED,
  WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN,
  WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED,
  WATCHBOX_FEATURE_DISABLED,
  WATCHBOX_NOT_FOUND,

  /** The supplied Watchbox description exceeds the durable maximum length. */
  WATCHBOX_INVALID_DESCRIPTION,

  /** The supplied Watchbox configuration references more layers than the durable maximum. */
  WATCHBOX_LAYER_LIMIT_EXCEEDED,

  /** One or more selected sources fail Watchbox object ID mapper eligibility. */
  WATCHBOX_SOURCE_ELIGIBILITY_FAILED,

  /** The supplied Watchbox filter has more predicates than the durable maximum. */
  WATCHBOX_FILTER_LIMIT_EXCEEDED,

  /**
   * Selected sources have an editable OBJECT_ID rule whose On Missing is not Error, which the
   * operator can fix. Distinct from {@link #WATCHBOX_SOURCE_ELIGIBILITY_FAILED}, where the cause is
   * unspecified.
   */
  WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR
}
