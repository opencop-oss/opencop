// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
final class RedisHistoricalJobAdmissionScriptGateway {

  private static final String REQUESTED_AT_ORDERING_FORMAT = "%019d:%09d";

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final HistoricalExecutionProperties properties;
  private final RedisScript<List<Object>> registerScript;
  private final RedisScript<List<Object>> recordExecutionExitScript;
  private final RedisScript<List<Object>> recordTerminalScript;
  private final RedisScript<String> cleanupAdmissionStateScript;
  private final RedisScript<String> readQueuedPayloadScript;

  RedisHistoricalJobAdmissionScriptGateway(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      HistoricalExecutionProperties properties,
      ProcessorHistoricalRedisScripts scripts) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.properties = properties;
    this.registerScript =
        keyLayout == HistoricalJobControlKeyLayout.CLUSTER
            ? scripts.admissionRegisterCluster()
            : scripts.admissionRegister();
    this.recordExecutionExitScript = scripts.admissionRecordExecutionExit();
    this.recordTerminalScript = scripts.admissionRecordTerminal();
    this.cleanupAdmissionStateScript =
        keyLayout == HistoricalJobControlKeyLayout.CLUSTER
            ? scripts.admissionCleanupStateCluster()
            : scripts.admissionCleanupState();
    this.readQueuedPayloadScript = scripts.admissionReadQueuedPayloadCluster();
  }

  List<Object> register(HistoricalTracksJobSubmission job, String serializedJob) {
    var keys =
        HistoricalJobAdmissionRedisKeys.registerScriptKeys(
            keyLayout, job.requestedBy(), job.jobId());
    if (keyLayout == HistoricalJobControlKeyLayout.CLUSTER) {
      return redisTemplate.execute(
          registerScript,
          keys,
          job.jobId().toString(),
          job.requestId().toString(),
          formatRequestedAt(job.requestedAt()),
          serializedJob,
          maxConcurrentSubjectsPerJob(),
          admissionTtlMillis(),
          job.requestedBy().toString());
    }
    return redisTemplate.execute(
        registerScript,
        keys,
        job.jobId().toString(),
        job.requestId().toString(),
        formatRequestedAt(job.requestedAt()),
        serializedJob,
        maxConcurrentSubjectsPerJob(),
        admissionTtlMillis());
  }

  List<Object> recordExecutionExit(UUID userId, UUID jobId) {
    return redisTemplate.execute(
        recordExecutionExitScript,
        HistoricalJobAdmissionRedisKeys.recordOutcomeScriptKeys(keyLayout, userId, jobId),
        jobId.toString(),
        admissionTtlMillis(),
        maxConcurrentSubjectsPerJob());
  }

  List<Object> recordTerminal(UUID userId, UUID jobId) {
    return redisTemplate.execute(
        recordTerminalScript,
        HistoricalJobAdmissionRedisKeys.recordOutcomeScriptKeys(keyLayout, userId, jobId),
        jobId.toString(),
        admissionTtlMillis(),
        maxConcurrentSubjectsPerJob());
  }

  String cleanupAdmissionState(UUID userId, String jobId) {
    return redisTemplate.execute(
        cleanupAdmissionStateScript,
        HistoricalJobAdmissionRedisKeys.cleanupScriptKeys(keyLayout, userId, jobId),
        jobId,
        admissionTtlMillis(),
        maxConcurrentSubjectsPerJob());
  }

  String readQueuedPayload(UUID userId, String jobId) {
    if (keyLayout == HistoricalJobControlKeyLayout.STANDALONE) {
      return redisTemplate.opsForValue().get(keyLayout.queuedPayloadKey(userId, jobId));
    }
    return redisTemplate.execute(
        readQueuedPayloadScript, List.of(keyLayout.queuedPayloadKey(userId, jobId)), jobId);
  }

  HistoricalJobControlKeyLayout keyLayout() {
    return keyLayout;
  }

  Duration admissionTtl() {
    return properties.getAdmissionTtl();
  }

  private String admissionTtlMillis() {
    return Long.toString(admissionTtl().toMillis());
  }

  private String maxConcurrentSubjectsPerJob() {
    return Integer.toString(properties.getMaxConcurrentSubjectsPerJob());
  }

  private static String formatRequestedAt(Instant requestedAt) {
    return REQUESTED_AT_ORDERING_FORMAT.formatted(
        requestedAt.getEpochSecond(), requestedAt.getNano());
  }
}
