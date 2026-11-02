// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout.StorageKind;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.HistoricalJobCancellation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
final class RedisHistoricalJobAdmissionQueuedPayloads {

  static final String PAYLOAD_RESTORE_FAILURE_REASON =
      "Historical job payload could not be restored from Redis";
  static final String SUPERSEDED_BY_REPLACEMENT_REASON = "Historical job superseded by replacement";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final RedisHistoricalJobAdmissionScriptGateway scriptGateway;
  private final HistoricalJobControlKeyLayout keyLayout;

  RedisHistoricalJobAdmissionQueuedPayloads(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      RedisHistoricalJobAdmissionScriptGateway scriptGateway) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.scriptGateway = scriptGateway;
    this.keyLayout = scriptGateway.keyLayout();
  }

  List<HistoricalTracksJobSubmission> restoreJobsToStart(
      UUID userId, String encodedJobIds, List<HistoricalJobCancellation> jobsToCancel) {
    if (encodedJobIds.isBlank()) {
      return List.of();
    }
    List<HistoricalTracksJobSubmission> restored = new ArrayList<>();
    for (String token : encodedJobIds.split(",")) {
      restoreJobToStart(userId, token, jobsToCancel, restored);
    }
    return List.copyOf(restored);
  }

  List<HistoricalJobCancellation> supersededCancellations(UUID userId, String encodedJobIds) {
    if (encodedJobIds.isBlank()) {
      return new ArrayList<>();
    }
    List<HistoricalJobCancellation> cancellations = new ArrayList<>();
    for (String token : encodedJobIds.split(",")) {
      if (token == null || token.isBlank()) {
        continue;
      }
      String cleanedToken = token.trim();
      UUID jobId = parseUuid(cleanedToken);
      if (jobId != null) {
        cancellations.add(new HistoricalJobCancellation(jobId, SUPERSEDED_BY_REPLACEMENT_REASON));
      }
      scriptGateway.cleanupAdmissionState(userId, cleanedToken);
    }
    return cancellations;
  }

  void appendPromotedCleanupStart(
      List<HistoricalTracksJobSubmission> jobsToStart,
      List<HistoricalJobCancellation> jobsToCancel,
      UUID userId,
      String promotedJobId) {
    if (promotedJobId == null || promotedJobId.isBlank()) {
      return;
    }
    var promotedJobs = restoreJobsToStart(userId, promotedJobId, jobsToCancel);
    jobsToStart.addAll(promotedJobs);
  }

  private void restoreJobToStart(
      UUID userId,
      String token,
      List<HistoricalJobCancellation> jobsToCancel,
      List<HistoricalTracksJobSubmission> restored) {
    if (token == null || token.isBlank()) {
      return;
    }
    String cleanedToken = token.trim();
    UUID jobId = parseUuid(cleanedToken);
    if (jobId == null) {
      appendPromotedCleanupStart(
          restored,
          jobsToCancel,
          userId,
          scriptGateway.cleanupAdmissionState(userId, cleanedToken));
      return;
    }
    restoreParsedJob(userId, jobId, jobsToCancel, restored);
  }

  private void restoreParsedJob(
      UUID userId,
      UUID jobId,
      List<HistoricalJobCancellation> jobsToCancel,
      List<HistoricalTracksJobSubmission> restored) {
    String payload = scriptGateway.readQueuedPayload(userId, jobId.toString());
    if (payload == null) {
      cancelAndPromoteNext(userId, jobId, jobsToCancel, restored);
      return;
    }
    try {
      JobSubmission parsed = objectMapper.readValue(payload, JobSubmission.class);
      if (!(parsed instanceof HistoricalTracksJobSubmission historicalJob)) {
        cancelAndPromoteNext(userId, jobId, jobsToCancel, restored);
        return;
      }
      deletePayload(userId, jobId);
      restored.add(historicalJob);
    } catch (Exception ignored) {
      cancelAndPromoteNext(userId, jobId, jobsToCancel, restored);
    }
  }

  private void cancelAndPromoteNext(
      UUID userId,
      UUID jobId,
      List<HistoricalJobCancellation> jobsToCancel,
      List<HistoricalTracksJobSubmission> restored) {
    jobsToCancel.add(new HistoricalJobCancellation(jobId, PAYLOAD_RESTORE_FAILURE_REASON));
    appendPromotedCleanupStart(
        restored,
        jobsToCancel,
        userId,
        scriptGateway.cleanupAdmissionState(userId, jobId.toString()));
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private void deletePayload(UUID userId, UUID jobId) {
    var address = keyLayout.queuedPayload(userId, jobId);
    if (address.storageKind() == StorageKind.PER_JOB_STRING) {
      redisTemplate.delete(address.redisKey());
      return;
    }
    redisTemplate
        .opsForHash()
        .delete(address.redisKey(), address.payloadField(), address.deadlineField());
  }
}
