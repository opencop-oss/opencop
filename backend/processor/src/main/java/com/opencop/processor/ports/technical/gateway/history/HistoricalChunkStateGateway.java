// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import java.util.Optional;

public interface HistoricalChunkStateGateway {

  void initialize(HistoricalChunkPlan plan);

  Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey);

  void transition(String chunkStateKey, HistoricalChunkStateStatus status);

  void updatePartCount(String chunkStateKey, int partCount);
}
