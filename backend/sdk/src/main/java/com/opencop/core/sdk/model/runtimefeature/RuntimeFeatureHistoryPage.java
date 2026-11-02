// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

import java.util.List;
import java.util.Objects;

/**
 * One deterministically ordered page of Runtime Feature successful-action history. Totals are
 * database counts rather than estimates; concurrent inserts can commit between the content and
 * count statements under Read Committed isolation.
 */
public record RuntimeFeatureHistoryPage(
    List<RuntimeFeatureHistoryEntry> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public RuntimeFeatureHistoryPage {
    content = List.copyOf(Objects.requireNonNull(content, "content"));
    if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException("Invalid Runtime Feature history page metadata");
    }
  }
}
