// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.helper.comparator;

import java.util.Comparator;

public class ApiVersionComparator implements Comparator<String> {

  private static final String SNAPSHOT = "-SNAPSHOT";

  @Override
  public int compare(String v1, String v2) {
    // Normalize versions
    boolean isSnapshot1 = v1.endsWith(SNAPSHOT);
    boolean isSnapshot2 = v2.endsWith(SNAPSHOT);

    String cleanV1 = isSnapshot1 ? v1.replace(SNAPSHOT, "") : v1;
    String cleanV2 = isSnapshot2 ? v2.replace(SNAPSHOT, "") : v2;

    String[] parts1 = cleanV1.split("\\.");
    String[] parts2 = cleanV2.split("\\.");

    int length = Math.max(parts1.length, parts2.length);
    for (int i = 0; i < length; i++) {
      int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
      int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

      if (p1 != p2) {
        return Integer.compare(p1, p2);
      }
    }

    // If versions are equal, check snapshot status
    if (isSnapshot1 && !isSnapshot2) {
      return -1;
    } else if (!isSnapshot1 && isSnapshot2) {
      return 1;
    }

    return 0;
  }
}
