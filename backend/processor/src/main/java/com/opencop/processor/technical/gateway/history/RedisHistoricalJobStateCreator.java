// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

final class RedisHistoricalJobStateCreator {

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> createReceivedIfAbsentScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  RedisHistoricalJobStateCreator(
      StringRedisTemplate redisTemplate, RedisScript<Long> createReceivedIfAbsentScript) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, createReceivedIfAbsentScript);
  }

  RedisHistoricalJobStateCreator(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      RedisScript<Long> createReceivedIfAbsentScript) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.createReceivedIfAbsentScript = createReceivedIfAbsentScript;
  }

  boolean createReceivedIfAbsent(
      HistoricalTracksJobSubmission job, HistoricalJobState received, long ttlMillis) {
    List<String> arguments = new ArrayList<>();
    received
        .toRedisHash()
        .forEach(
            (field, value) -> {
              arguments.add(field);
              arguments.add(value);
            });
    arguments.add(Long.toString(ttlMillis));
    arguments.add(job.jobId().toString());
    arguments.add(job.requestId().toString());
    Long created =
        redisTemplate.execute(createReceivedIfAbsentScript, scriptKeys(job), arguments.toArray());
    return created != null && created == 1L;
  }

  boolean createReceivedIfAbsent(
      HistoricalSubjectExecutionMeta laneMetadata, HistoricalJobState received, long ttlMillis) {
    List<String> arguments = new ArrayList<>();
    received
        .toRedisHash()
        .forEach(
            (field, value) -> {
              arguments.add(field);
              arguments.add(value);
            });
    arguments.add(Long.toString(ttlMillis));
    arguments.add(laneMetadata.laneJobId().toString());
    arguments.add(laneMetadata.requestId().toString());
    Long created =
        redisTemplate.execute(
            createReceivedIfAbsentScript, scriptKeys(laneMetadata), arguments.toArray());
    return created != null && created == 1L;
  }

  private List<String> scriptKeys(HistoricalTracksJobSubmission job) {
    return List.of(
        keyLayout.jobState(job.jobId()),
        keyLayout.userJobIndex(job.requestedBy()),
        keyLayout.userRequestJobIndex(job.requestedBy(), job.requestId()),
        keyLayout.userRequestIndex(job.requestedBy()),
        keyLayout.userJobSequence(job.requestedBy()),
        keyLayout.userRequestJobSequence(job.requestedBy(), job.requestId()));
  }

  private List<String> scriptKeys(HistoricalSubjectExecutionMeta laneMetadata) {
    return List.of(
        keyLayout.jobState(laneMetadata.laneJobId()),
        keyLayout.userJobIndex(laneMetadata.requestedBy()),
        keyLayout.userRequestJobIndex(laneMetadata.requestedBy(), laneMetadata.requestId()),
        keyLayout.userRequestIndex(laneMetadata.requestedBy()),
        keyLayout.userJobSequence(laneMetadata.requestedBy()),
        keyLayout.userRequestJobSequence(laneMetadata.requestedBy(), laneMetadata.requestId()));
  }
}
