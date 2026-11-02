// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoricalTracksAckStatus;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalTracksAckFactoryTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID SOURCE_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final UUID LAYER_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440124");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440140");
  private static final UUID JOB_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440141");
  private static final UUID JOB_ID_3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");

  private final HistoricalTracksAckFactory factory = new HistoricalTracksAckFactory();

  @Test
  void accepted_flattenChildSubjectsFromAllSubmissions() {
    var submissions = new ArrayList<HistoricalTracksJobSubmission>();
    submissions.add(
        submission(
            List.of(
                subject(SubjectType.SOURCE, SOURCE_ID, JOB_ID),
                subject(SubjectType.LAYER, LAYER_ID, JOB_ID_2))));
    submissions.add(submission(subject(SubjectType.SOURCE, SOURCE_ID_2, JOB_ID_3)));
    var ack = factory.accepted(REQUEST_ID, submissions);

    assertEquals(REQUEST_ID, ack.requestId());
    assertEquals(HistoricalTracksAckStatus.ACCEPTED, ack.status());
    assertEquals(3, ack.subjects().size());
    assertEquals(SubjectType.SOURCE, ack.subjects().get(0).subjectType());
    assertEquals(SOURCE_ID, ack.subjects().get(0).subjectId());
    assertEquals(JOB_ID, ack.subjects().get(0).jobId());
    assertEquals(SubjectType.LAYER, ack.subjects().get(1).subjectType());
    assertEquals(LAYER_ID, ack.subjects().get(1).subjectId());
    assertEquals(JOB_ID_2, ack.subjects().get(1).jobId());
    assertEquals(SubjectType.SOURCE, ack.subjects().get(2).subjectType());
    assertEquals(SOURCE_ID_2, ack.subjects().get(2).subjectId());
    assertEquals(JOB_ID_3, ack.subjects().get(2).jobId());
  }

  @Test
  void accepted_preservesSubmissionOrderAndSubjectOrder() {
    var submissions = new ArrayList<HistoricalTracksJobSubmission>();
    submissions.add(
        submission(
            List.of(
                subject(SubjectType.LAYER, LAYER_ID, JOB_ID),
                subject(SubjectType.LAYER, LAYER_ID_2, JOB_ID_2),
                subject(SubjectType.SOURCE, SOURCE_ID, JOB_ID_3))));
    var ack = factory.accepted(REQUEST_ID, submissions);

    assertEquals(REQUEST_ID, ack.requestId());
    assertEquals(HistoricalTracksAckStatus.ACCEPTED, ack.status());
    assertEquals(3, ack.subjects().size());
    assertEquals(SubjectType.LAYER, ack.subjects().get(0).subjectType());
    assertEquals(LAYER_ID, ack.subjects().get(0).subjectId());
    assertEquals(JOB_ID, ack.subjects().get(0).jobId());
    assertEquals(SubjectType.LAYER, ack.subjects().get(1).subjectType());
    assertEquals(LAYER_ID_2, ack.subjects().get(1).subjectId());
    assertEquals(JOB_ID_2, ack.subjects().get(1).jobId());
    assertEquals(SubjectType.SOURCE, ack.subjects().get(2).subjectType());
    assertEquals(SOURCE_ID, ack.subjects().get(2).subjectId());
    assertEquals(JOB_ID_3, ack.subjects().get(2).jobId());
  }

  private static HistoricalTracksJobSubmission submission(List<HistoricalTracksSubject> subjects) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        subjects,
        USER_ID,
        Instant.parse("2026-04-06T09:59:59Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        120,
        List.of());
  }

  private static HistoricalTracksJobSubmission submission(HistoricalTracksSubject subject) {
    return submission(List.of(subject));
  }

  private static HistoricalTracksSubject subject(
      SubjectType subjectType, UUID subjectId, UUID jobId) {
    return new HistoricalTracksSubject(jobId, subjectType, subjectId);
  }
}
