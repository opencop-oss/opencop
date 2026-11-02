// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class HistoricalSubjectJobFactory {

  public List<HistoricalSubjectExecutionMeta> create(
      HistoricalTracksJobSubmission parentSubmission, HistoricalChunkWindowPlan windowPlan) {
    return parentSubmission.subjects().stream()
        .map(subject -> createMeta(parentSubmission, windowPlan, subject))
        .toList();
  }

  private HistoricalSubjectExecutionMeta createMeta(
      HistoricalTracksJobSubmission parentSubmission,
      HistoricalChunkWindowPlan windowPlan,
      HistoricalTracksSubject subject) {
    Objects.requireNonNull(parentSubmission, "parentSubmission");
    Objects.requireNonNull(windowPlan, "windowPlan");
    return new HistoricalSubjectExecutionMeta(
        subject.jobId(),
        parentSubmission.jobId(),
        parentSubmission.requestId(),
        parentSubmission.issuer(),
        parentSubmission.requestedBy(),
        parentSubmission.requestedAt(),
        parentSubmission.requestedFrom(),
        parentSubmission.requestedTo(),
        parentSubmission.effectiveFrom(),
        parentSubmission.effectiveTo(),
        parentSubmission.resolution(),
        parentSubmission.plannedChunks(),
        parentSubmission.filters(),
        subject.subjectType(),
        subject.subjectId(),
        windowPlan);
  }
}
