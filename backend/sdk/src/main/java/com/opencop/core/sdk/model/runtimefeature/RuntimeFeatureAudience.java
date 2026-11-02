// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

public sealed interface RuntimeFeatureAudience
    permits RuntimeFeatureAudience.None,
        RuntimeFeatureAudience.All,
        RuntimeFeatureAudience.UserGroups {

  record None() implements RuntimeFeatureAudience {}

  record All() implements RuntimeFeatureAudience {}

  /**
   * Enabled iff the caller is a current direct member of any listed group. Group IDs may refer to
   * groups that were later hard-deleted; those stale targets resolve to nobody (fail closed).
   */
  record UserGroups(SortedSet<UUID> userGroupIds) implements RuntimeFeatureAudience {

    public static final int MAX_USER_GROUP_IDS = 100;

    public UserGroups {
      if (userGroupIds == null
          || userGroupIds.isEmpty()
          || userGroupIds.size() > MAX_USER_GROUP_IDS
          || userGroupIds.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException(
            "userGroupIds must contain between 1 and " + MAX_USER_GROUP_IDS + " non-null ids");
      }
      var sortedCopy = new TreeSet<UUID>();
      sortedCopy.addAll(userGroupIds);
      userGroupIds = Collections.unmodifiableSortedSet(sortedCopy);
    }
  }
}
