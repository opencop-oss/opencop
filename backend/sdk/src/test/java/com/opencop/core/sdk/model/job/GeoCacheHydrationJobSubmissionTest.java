// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.opencop.core.sdk.model.cache.GeoCacheHydrationJob;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GeoCacheHydrationJobSubmissionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void topicAndKey_matchHydrationRequest() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655446000");
    var issuer = JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099"));
    UUID sourceId = UUID.fromString("550e8400-e29b-41d4-a716-446655446001");
    var submission =
        new GeoCacheHydrationJobSubmission(
            jobId,
            issuer,
            sourceId,
            GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY,
            GeoCacheHydrationJob.Trigger.MANUAL);

    assertEquals(JobSubmission.TOPIC, submission.topic());
    assertEquals(sourceId.toString(), submission.key());
    assertEquals(jobId, submission.jobId());
    assertEquals(issuer, submission.issuer());
    assertEquals(sourceId, submission.sourceId());
    assertEquals(GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY, submission.mode());
    assertEquals(GeoCacheHydrationJob.Trigger.MANUAL, submission.trigger());
  }

  @Test
  void roundTripsThroughJobSubmissionTypeInfo() throws Exception {
    var submission =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
            UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
            GeoCacheHydrationJob.Trigger.MANUAL);

    JobSubmission deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(submission), JobSubmission.class);

    var hydrationSubmission = assertInstanceOf(GeoCacheHydrationJobSubmission.class, deserialized);
    assertEquals(submission, hydrationSubmission);
  }

  @Test
  void supportsSystemIssuer() {
    var submission =
        GeoCacheHydrationJobSubmission.forSource(
            JobIssuer.system("geo-cache-repair"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655446001"),
            GeoCacheHydrationJob.Trigger.MANUAL);

    assertEquals(JobIssuerType.SYSTEM, submission.issuer().type());
    assertEquals("geo-cache-repair", submission.issuer().id());
  }
}
