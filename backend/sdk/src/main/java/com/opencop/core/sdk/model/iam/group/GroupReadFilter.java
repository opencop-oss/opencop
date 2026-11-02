// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.group;

/**
 * Optional server-side filters for listing user groups. A null field does not constrain the query.
 * {@code search} is matched case-insensitively against the group's name.
 */
public record GroupReadFilter(String search) {

  public static GroupReadFilter empty() {
    return new GroupReadFilter(null);
  }

  public boolean hasSearch() {
    return search != null && !search.isBlank();
  }
}
