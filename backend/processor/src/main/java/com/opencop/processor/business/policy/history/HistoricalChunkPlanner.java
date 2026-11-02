// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HistoricalChunkPlanner {

  public HistoricalChunkWindowPlan planWindow(HistoricalTracksJobSubmission job) {
    List<HistoricalChunkPlanEntry> entries = new ArrayList<>();
    Instant from = job.effectiveFrom();
    int chunkIndex = 0;

    while (from.isBefore(job.effectiveTo())) {
      Instant to = from.plus(job.resolution().chunkSize());
      entries.add(
          new HistoricalChunkPlanEntry(chunkIndex, chunkKey(job, from), null, null, null, from, to)
              .withoutCacheAnalysis());
      from = to;
      chunkIndex++;
    }

    HistoricalChunkWindowPlan windowPlan =
        new HistoricalChunkWindowPlan(
            job.effectiveFrom(), job.effectiveTo(), entries.size(), job.resolution(), entries);
    validateAgainstWindow(job, windowPlan);
    return windowPlan;
  }

  String chunkKey(HistoricalTracksJobSubmission job, Instant chunkFrom) {
    return "opencop:history:chunk:%s:%s:%s"
        .formatted(job.resolution().name(), HistoricalChunkPlan.PLANNING_VERSION, chunkFrom);
  }

  private void validateAgainstWindow(
      HistoricalTracksJobSubmission job, HistoricalChunkWindowPlan plan) {
    if (job.plannedChunks() != plan.chunks()
        || !job.effectiveFrom().equals(plan.effectiveFrom())
        || !job.effectiveTo().equals(plan.effectiveTo())
        || job.resolution() != plan.resolution()) {
      throw new IllegalStateException(
          "Processor chunk window plan does not match historical job submission metadata");
    }
  }
}
