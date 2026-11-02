// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.util.function.Consumer;

public interface HistoricalChunkReader {

  void readChunk(
      HistoricalExecutionContext executionContext,
      HistoricalChunkPlanEntry chunk,
      Consumer<HistoricalSourceEvent> rowConsumer);
}
