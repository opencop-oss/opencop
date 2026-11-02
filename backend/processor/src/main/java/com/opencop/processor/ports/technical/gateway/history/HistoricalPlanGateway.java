// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import java.util.Optional;
import java.util.UUID;

public interface HistoricalPlanGateway {

  void save(HistoricalChunkPlan plan);

  Optional<HistoricalChunkPlan> findByJobId(UUID jobId);
}
