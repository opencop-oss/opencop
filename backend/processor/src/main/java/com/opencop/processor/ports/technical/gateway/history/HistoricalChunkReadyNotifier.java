// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;

public interface HistoricalChunkReadyNotifier {

  void notifyChunkReady(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      boolean cached,
      int partCount);
}
