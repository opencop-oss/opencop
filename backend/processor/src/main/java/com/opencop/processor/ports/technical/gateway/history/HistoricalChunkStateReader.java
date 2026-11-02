// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.time.Instant;
import java.util.function.Consumer;

public interface HistoricalChunkStateReader {

  void readInitialState(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Consumer<HistoricalSourceEvent> rowConsumer);

  void readStateDelta(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Instant previousChunkEndExclusive,
      Consumer<HistoricalSourceEvent> rowConsumer);
}
