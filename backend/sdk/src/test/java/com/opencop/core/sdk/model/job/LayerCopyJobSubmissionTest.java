// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LayerCopyJobSubmissionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void topicAndKey_matchTargetLayer() {
    UUID jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655446010");
    var issuer = JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099"));
    UUID sourceLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446011");
    UUID targetLayerId = UUID.fromString("550e8400-e29b-41d4-a716-446655446012");
    var submission =
        new LayerCopyJobSubmission(jobId, issuer, sourceLayerId, targetLayerId, true, false);

    assertEquals(JobSubmission.TOPIC, submission.topic());
    assertEquals(targetLayerId.toString(), submission.key());
    assertEquals(jobId, submission.jobId());
    assertEquals(issuer, submission.issuer());
    assertEquals(sourceLayerId, submission.sourceLayerId());
    assertEquals(targetLayerId, submission.targetLayerId());
  }

  @Test
  void roundTripsThroughJobSubmissionTypeInfo() throws Exception {
    var submission =
        LayerCopyJobSubmission.create(
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655446099")),
            UUID.fromString("550e8400-e29b-41d4-a716-446655446011"),
            UUID.fromString("550e8400-e29b-41d4-a716-446655446012"),
            true,
            false);

    JobSubmission deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(submission), JobSubmission.class);

    var layerCopySubmission = assertInstanceOf(LayerCopyJobSubmission.class, deserialized);
    assertEquals(submission, layerCopySubmission);
  }
}
