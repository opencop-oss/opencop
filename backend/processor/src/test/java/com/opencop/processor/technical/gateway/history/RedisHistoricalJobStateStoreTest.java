// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobStatus;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisHistoricalJobStateStoreTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);

  private final HistoricalExecutionProperties properties = new HistoricalExecutionProperties();
  private final RedisHistoricalJobStateStore store =
      new RedisHistoricalJobStateStore(redisTemplate, properties);

  @BeforeEach
  void setUp() {
    properties.setJobStateTtl(Duration.ofHours(24));
    when(redisTemplate.opsForHash()).thenReturn(hashOperations);
  }

  @Test
  void findByJobId_whenHashMissing_returnsEmpty() {
    var jobId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    when(hashOperations.entries(HistoricalJobControlKeyLayout.STANDALONE.jobState(jobId)))
        .thenReturn(Map.of());

    var state = store.findByJobId(jobId);

    assertTrue(state.isEmpty());
  }

  @Test
  void findByJobId_mapsRedisHashToState() {
    var expected = HistoricalJobState.received(job(), now(), properties.getJobStateTtl());
    when(hashOperations.entries(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(expected.jobId())))
        .thenReturn(redisHash(expected));

    var state = store.findByJobId(expected.jobId()).orElseThrow();

    assertEquals(HistoricalJobStatus.RECEIVED, state.status());
    assertEquals(HistoricalJobStage.INGRESS_RECEIVED, state.stage());
    assertEquals(expected.jobId(), state.jobId());
    assertEquals(expected.requestId(), state.requestId());
  }

  @Test
  void persistWithTtl_writesHashAndRefreshesTtl() {
    var state = HistoricalJobState.received(job(), now(), properties.getJobStateTtl());

    store.persistWithTtl(state);

    verify(hashOperations)
        .putAll(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(state.jobId()), state.toRedisHash());
    verify(redisTemplate)
        .expire(
            HistoricalJobControlKeyLayout.STANDALONE.jobState(state.jobId()), Duration.ofHours(24));
  }

  private static Map<Object, Object> redisHash(HistoricalJobState state) {
    Map<Object, Object> hash = new LinkedHashMap<>();
    hash.putAll(state.toRedisHash());
    return hash;
  }

  private static Instant now() {
    return Instant.parse("2026-04-07T12:00:00Z");
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440121"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        118,
        List.of());
  }
}
