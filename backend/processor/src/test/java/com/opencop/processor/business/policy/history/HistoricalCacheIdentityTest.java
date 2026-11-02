// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalCacheIdentityTest {

  private static final UUID REQUESTED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID PARENT_JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final UUID LANE_JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440131");

  private final HistoricalCacheIdentity cacheIdentity =
      new HistoricalCacheIdentity(new ObjectMapper());

  @Test
  void payloadAndStateKeysMatchWhenUsingMetadataIdentity() {
    var laneMetadata = metadata();
    var chunkFrom = Instant.parse("2026-04-06T10:02:00Z");

    assertEquals(
        "opencop:history:payload:SOURCE:550e8400-e29b-41d4-a716-446655440122:MINUTE_1:v1:v2_latest_in_bucket:55ef966aa5dd2c3d760c64a072a6329d22e67e4450235bfc7c9d8864ada15598:2026-04-06T10:02:00Z",
        cacheIdentity.payloadKey(laneMetadata, chunkFrom));
    assertEquals(
        "opencop:history:chunk-state:SOURCE:550e8400-e29b-41d4-a716-446655440122:MINUTE_1:v1:v2_latest_in_bucket:55ef966aa5dd2c3d760c64a072a6329d22e67e4450235bfc7c9d8864ada15598:2026-04-06T10:02:00Z",
        cacheIdentity.chunkStateKey(laneMetadata, chunkFrom));
  }

  private static HistoricalSubjectExecutionMeta metadata() {
    return new HistoricalSubjectExecutionMeta(
        LANE_JOB_ID,
        PARENT_JOB_ID,
        REQUEST_ID,
        JobIssuer.user(REQUESTED_BY),
        REQUESTED_BY,
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(new HistoricalFilterRequest("source", Map.of("include", "all"))),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440122"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            132,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }
}
