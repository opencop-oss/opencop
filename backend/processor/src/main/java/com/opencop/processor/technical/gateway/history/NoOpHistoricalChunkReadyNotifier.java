// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkReadyNotifier;

public class NoOpHistoricalChunkReadyNotifier implements HistoricalChunkReadyNotifier {

  @Override
  public void notifyChunkReady(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      boolean cached,
      int partCount) {}
}
