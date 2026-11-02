// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model;

/** Bounding free text so it cannot overflow the column it is copied into. */
public final class BoundedText {

  private BoundedText() {}

  /**
   * Cuts to {@code maximum} code points, so a surrogate pair is never split in half and the result
   * is still valid text rather than a lone half of a character.
   */
  public static String truncate(String value, int maximum) {
    if (value == null) {
      return null;
    }
    var points = value.codePointCount(0, value.length());
    return points <= maximum ? value : value.substring(0, value.offsetByCodePoints(0, maximum));
  }
}
