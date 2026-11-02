// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.user;

/**
 * Optional server-side filters for listing users. All fields are nullable; a null field means "do
 * not constrain on this attribute". {@code search} is a free-text term matched (case-insensitively)
 * against the user's name/username/email fields.
 */
public record UserReadFilter(String search, Boolean active, Boolean admin) {

  public static UserReadFilter empty() {
    return new UserReadFilter(null, null, null);
  }

  public boolean hasSearch() {
    return search != null && !search.isBlank();
  }
}
