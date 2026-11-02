// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.model.job;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobType;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record HistoricalJobState(
    UUID jobId,
    UUID requestId,
    String jobType,
    SubjectType subjectType,
    UUID subjectId,
    UUID requestedBy,
    Instant requestedAt,
    Instant requestedFrom,
    Instant requestedTo,
    Instant effectiveFrom,
    Instant effectiveTo,
    HistoricalResolution resolution,
    long plannedChunks,
    HistoricalJobStatus status,
    HistoricalJobStage stage,
    Instant startedAt,
    Instant updatedAt,
    Instant expiresAt,
    String failureReason,
    String workerId,
    long userSequence,
    long requestSequence,
    String queueRole) {

  public static final String QUEUE_ROLE_DISPATCH_READY = HistoricalJobQueueRole.DISPATCH_READY;
  public static final String QUEUE_ROLE_ACTIVE_REQUEST_BACKLOG =
      HistoricalJobQueueRole.ACTIVE_REQUEST_BACKLOG;
  public static final String QUEUE_ROLE_QUEUED_REPLACEMENT =
      HistoricalJobQueueRole.QUEUED_REPLACEMENT;

  public static HistoricalJobState received(
      HistoricalTracksJobSubmission job, Instant now, Duration ttl) {
    HistoricalJobSubjectIdentity subjectIdentity = HistoricalJobSubjectIdentity.fromParentJob(job);
    return receivedState(
        job.jobId(),
        job.requestId(),
        job.jobType().name(),
        subjectIdentity.subjectType(),
        subjectIdentity.subjectId(),
        job.requestedBy(),
        job.requestedAt(),
        job.requestedFrom(),
        job.requestedTo(),
        job.effectiveFrom(),
        job.effectiveTo(),
        job.resolution(),
        job.plannedChunks(),
        now,
        ttl);
  }

  public static HistoricalJobState received(
      HistoricalSubjectExecutionMeta laneMetadata, Instant now, Duration ttl) {
    return receivedState(
        laneMetadata.laneJobId(),
        laneMetadata.requestId(),
        JobType.HISTORICAL_TRACKS.name(),
        laneMetadata.subjectType(),
        laneMetadata.subjectId(),
        laneMetadata.requestedBy(),
        laneMetadata.requestedAt(),
        laneMetadata.requestedFrom(),
        laneMetadata.requestedTo(),
        laneMetadata.effectiveFrom(),
        laneMetadata.effectiveTo(),
        laneMetadata.resolution(),
        laneMetadata.plannedChunks(),
        now,
        ttl);
  }

  private static HistoricalJobState receivedState(
      UUID jobId,
      UUID requestId,
      String jobType,
      SubjectType subjectType,
      UUID subjectId,
      UUID requestedBy,
      Instant requestedAt,
      Instant requestedFrom,
      Instant requestedTo,
      Instant effectiveFrom,
      Instant effectiveTo,
      HistoricalResolution resolution,
      long plannedChunks,
      Instant now,
      Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.RECEIVED,
        HistoricalJobStage.INGRESS_RECEIVED,
        null,
        now,
        now.plus(ttl),
        null,
        null,
        0L,
        0L,
        "");
  }

  public boolean isTerminal() {
    return status.isTerminal();
  }

  public HistoricalJobState queued(Instant now, Duration ttl) {
    return queued(now, ttl, HistoricalJobQueueRole.DISPATCH_READY);
  }

  public HistoricalJobState queued(Instant now, Duration ttl, String newQueueRole) {
    HistoricalJobQueueRole.validate(newQueueRole);
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.QUEUED,
        HistoricalJobStage.INGRESS_QUEUED,
        startedAt,
        now,
        now.plus(ttl),
        null,
        workerId,
        userSequence,
        requestSequence,
        newQueueRole);
  }

  public HistoricalJobState running(Instant now, Duration ttl, String newWorkerId) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.RUNNING,
        HistoricalJobStage.EXECUTION_RUNNING,
        startedAt == null ? now : startedAt,
        now,
        now.plus(ttl),
        null,
        newWorkerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState planning(Instant now, Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.RUNNING,
        HistoricalJobStage.PLANNING,
        startedAt,
        now,
        now.plus(ttl),
        null,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState planned(Instant now, Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.RUNNING,
        HistoricalJobStage.PLANNED,
        startedAt,
        now,
        now.plus(ttl),
        null,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState completed(Instant now, Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.COMPLETED,
        HistoricalJobStage.EXECUTION_COMPLETED,
        startedAt,
        now,
        now.plus(ttl),
        null,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState deliveryPending(Instant now, Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.RUNNING,
        HistoricalJobStage.DELIVERY_PENDING,
        startedAt,
        now,
        now.plus(ttl),
        null,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState failed(
      Instant now, Duration ttl, HistoricalJobStage failureStage, String newFailureReason) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.FAILED,
        failureStage,
        startedAt,
        now,
        now.plus(ttl),
        newFailureReason,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState cancellationRequested(Instant now, Duration ttl, String reason) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        status,
        HistoricalJobStage.CANCELLATION_REQUESTED,
        startedAt,
        now,
        now.plus(ttl),
        reason,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState cancelled(Instant now, String reason) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        HistoricalJobStatus.CANCELLED,
        HistoricalJobStage.CANCELLED,
        startedAt,
        now,
        expiresAt,
        reason,
        workerId,
        userSequence,
        requestSequence,
        "");
  }

  public HistoricalJobState refresh(Instant now, Duration ttl) {
    return new HistoricalJobState(
        jobId,
        requestId,
        jobType,
        subjectType,
        subjectId,
        requestedBy,
        requestedAt,
        requestedFrom,
        requestedTo,
        effectiveFrom,
        effectiveTo,
        resolution,
        plannedChunks,
        status,
        stage,
        startedAt,
        now,
        now.plus(ttl),
        failureReason,
        workerId,
        userSequence,
        requestSequence,
        queueRole);
  }

  public Map<String, String> toRedisHash() {
    return HistoricalJobStateRedisHashMapper.toRedisHash(this);
  }

  public static HistoricalJobState fromRedisHash(Map<Object, Object> hash) {
    return HistoricalJobStateRedisHashMapper.fromRedisHash(hash);
  }
}
