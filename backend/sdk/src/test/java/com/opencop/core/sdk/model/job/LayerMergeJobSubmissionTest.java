// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

class LayerMergeJobSubmissionTest {

  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447000");
  private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655447001");
  private static final UUID TARGET_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447002");
  private static final UUID FIRST_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447003");
  private static final UUID SECOND_SOURCE_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655447004");
  private static final JobIssuer ISSUER = JobIssuer.user(ACTOR_ID);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTripsThroughJobSubmissionTypeInfoWithOrderedSources() throws Exception {
    var submission =
        new LayerMergeJobSubmission(
            JOB_ID,
            ISSUER,
            TARGET_LAYER_ID,
            List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID));

    JobSubmission deserialized =
        objectMapper.readValue(objectMapper.writeValueAsBytes(submission), JobSubmission.class);

    var mergeSubmission = assertInstanceOf(LayerMergeJobSubmission.class, deserialized);
    assertEquals(submission, mergeSubmission);
    assertEquals(TARGET_LAYER_ID.toString(), mergeSubmission.key());
    assertEquals(ACTOR_ID.toString(), mergeSubmission.issuer().id());
    assertEquals(
        List.of(FIRST_SOURCE_LAYER_ID, SECOND_SOURCE_LAYER_ID), mergeSubmission.sourceLayerIds());
  }

  @Test
  void deserializationRejectsPayloadMissingTargetLayerId() {
    String missingTargetLayerPayload =
        """
            {"type":"LAYER_MERGE","jobId":"550e8400-e29b-41d4-a716-446655447000","issuer":{"type":"USER","id":"550e8400-e29b-41d4-a716-446655447001"},"sourceLayerIds":["550e8400-e29b-41d4-a716-446655447003","550e8400-e29b-41d4-a716-446655447004"]}""";

    assertThrows(
        ValueInstantiationException.class,
        () ->
            objectMapper.readValue(
                missingTargetLayerPayload.getBytes(StandardCharsets.UTF_8), JobSubmission.class));
  }
}
