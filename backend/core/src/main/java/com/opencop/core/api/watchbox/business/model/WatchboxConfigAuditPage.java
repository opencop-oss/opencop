// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import java.util.List;
import java.util.Objects;

/**
 * One deterministically ordered page of Watchbox configuration audit history. Totals are database
 * counts rather than estimates; concurrent inserts can commit between the content and count
 * statements under Read Committed isolation.
 */
public record WatchboxConfigAuditPage(
    List<WatchboxConfigAuditEntry> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public WatchboxConfigAuditPage {
    content = List.copyOf(Objects.requireNonNull(content, "content"));
    if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
      throw new IllegalArgumentException("Invalid Watchbox configuration audit page metadata");
    }
  }
}
