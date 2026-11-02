// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import com.opencop.core.sdk.model.history.HistoricalSubjectAck;
import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksAckStatus;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HistoricalTracksAckFactory {

  public HistoricalTracksAck accepted(
      UUID requestId, List<HistoricalTracksJobSubmission> submissions) {
    // Build the ack from the submissions so each accepted subject carries the job id that its
    // chunks will be stamped with. The ids are generated synchronously above, so the client learns
    // its job id before any chunk arrives.
    List<HistoricalSubjectAck> acceptedSubjects =
        submissions.stream()
            .flatMap(submission -> toSubjectAcks(submission.subjects()).stream())
            .toList();
    return new HistoricalTracksAck(requestId, HistoricalTracksAckStatus.ACCEPTED, acceptedSubjects);
  }

  private List<HistoricalSubjectAck> toSubjectAcks(List<HistoricalTracksSubject> subjects) {
    return subjects.stream()
        .map(
            subject ->
                new HistoricalSubjectAck(
                    subject.subjectType(), subject.subjectId(), subject.jobId()))
        .toList();
  }
}
