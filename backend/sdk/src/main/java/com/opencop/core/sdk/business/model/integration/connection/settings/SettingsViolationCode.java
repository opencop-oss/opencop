// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.settings;

/** Codes of the rules the host enforces on every connection's settings. */
public enum SettingsViolationCode {
  /** The schema declares no connection settings, yet settings were supplied. */
  SETTINGS_NOT_SUPPORTED,
  /** The schema declares no section of that name. */
  UNKNOWN_SECTION,
  /** The section declares no field of that name. */
  UNKNOWN_FIELD,
  /** A section or field was given an explicit null; omitting it is how a default is inherited. */
  NULL_VALUE,
  /** The JSON type does not match the section or field kind. */
  INVALID_TYPE,
  /** A duration field holds text that is not an ISO-8601 duration. */
  INVALID_DURATION,
  /** A text field is empty or only whitespace once stripped. */
  BLANK_TEXT,
  /**
   * Text holds a control character, an unpaired surrogate or a Unicode noncharacter, which an
   * integration cannot send in its messages.
   */
  INVALID_CHARACTER,
  /** A text field is longer than its declared maximum length. */
  TOO_LONG,
  /** A number field declared whole holds a fraction. */
  NOT_INTEGER,
  /** A number or duration is below its declared minimum. */
  BELOW_MINIMUM,
  /** A number or duration is above its declared maximum. */
  ABOVE_MAXIMUM,
  /** A choice field holds a value outside its options, compared exactly. */
  NOT_AN_OPTION
}
