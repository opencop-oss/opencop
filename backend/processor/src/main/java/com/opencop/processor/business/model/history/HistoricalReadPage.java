// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import java.util.List;

public record HistoricalReadPage(
    List<HistoricalSourceEvent> rows, HistoricalReadCursor nextCursor, boolean hasMore) {

  public HistoricalReadPage {
    rows = List.copyOf(rows);
  }
}
