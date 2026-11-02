// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.history;

import java.util.Objects;
import java.util.UUID;

/**
 * Topology-selected Redis key layout for historical job control state.
 *
 * <p>The standalone layout reproduces the existing key names. The cluster layout places every
 * historical control key behind one fixed Redis hash tag, so a control operation can declare all of
 * its keys to one cluster slot without changing job or user identity values.
 */
public enum HistoricalJobControlKeyLayout {
  /** Uses the legacy untagged Redis control-key addressing. */
  STANDALONE,

  /** Places control keys behind one fixed Redis hash tag and shared control slot. */
  CLUSTER;

  public static final String STANDALONE_PREFIX = "opencop:history";
  public static final String CLUSTER_PREFIX = "opencop:history:{history}";
  public static final String STANDALONE_ACTIVE_RUNNING_SCAN_PATTERN =
      "opencop:history:admission:user:*:active-running";
  public static final String CLUSTER_ACTIVE_RUNNING_SCAN_PATTERN =
      "opencop:history:{history}:admission:user:*:active-running";

  private static final String STANDALONE_JOB_STATE_PREFIX = STANDALONE_PREFIX + ":job:";
  private static final String STANDALONE_USER_JOB_INDEX_PREFIX =
      STANDALONE_PREFIX + ":job-index:user:";
  private static final String STANDALONE_USER_REQUEST_INDEX_PREFIX =
      STANDALONE_PREFIX + ":request-index:user:";
  private static final String STANDALONE_USER_JOB_SEQUENCE_PREFIX =
      STANDALONE_PREFIX + ":job-sequence:user:";
  private static final String STANDALONE_ADMISSION_USER_PREFIX =
      STANDALONE_PREFIX + ":admission:user:";
  private static final String STANDALONE_JOB_META_PREFIX =
      STANDALONE_PREFIX + ":admission:job-meta:";
  private static final String STANDALONE_JOB_PAYLOAD_PREFIX = STANDALONE_PREFIX + ":job-payload:";

  private static final String REQUEST_SUFFIX = ":request:";
  private static final String ACTIVE_REQUEST_SUFFIX = ":active-request";
  private static final String ACTIVE_RUNNING_SUFFIX = ":active-running";
  private static final String ACTIVE_BACKLOG_SUFFIX = ":active-backlog";
  private static final String REPLACEMENT_REQUEST_SUFFIX = ":replacement-request";
  private static final String REPLACEMENT_JOBS_SUFFIX = ":replacement-jobs";
  private static final String CLUSTER_USER_JOB_INDEX_SUFFIX = ":job-index:user:";
  private static final String CLUSTER_USER_REQUEST_INDEX_SUFFIX = ":request-index:user:";
  private static final String CLUSTER_USER_JOB_SEQUENCE_SUFFIX = ":job-sequence:user:";
  private static final String CLUSTER_ADMISSION_USER_PREFIX = ":admission:user:";
  private static final String CLUSTER_JOB_META_PREFIX = ":admission:job-meta:";
  private static final String CLUSTER_QUEUED_PAYLOAD_SUFFIX = ":queued-payloads";
  private static final String DEADLINE_SUFFIX = ":expiresAt";

  public String jobState(UUID jobId) {
    return switch (this) {
      case STANDALONE -> STANDALONE_JOB_STATE_PREFIX + require(jobId, "jobId");
      case CLUSTER -> CLUSTER_PREFIX + ":job:" + require(jobId, "jobId");
    };
  }

  public String userJobIndex(UUID userId) {
    return switch (this) {
      case STANDALONE -> STANDALONE_USER_JOB_INDEX_PREFIX + require(userId, "userId");
      case CLUSTER -> CLUSTER_PREFIX + CLUSTER_USER_JOB_INDEX_SUFFIX + require(userId, "userId");
    };
  }

  public String userRequestJobIndex(UUID userId, UUID requestId) {
    return userJobIndex(userId) + REQUEST_SUFFIX + require(requestId, "requestId");
  }

  public String userRequestIndex(UUID userId) {
    return switch (this) {
      case STANDALONE -> STANDALONE_USER_REQUEST_INDEX_PREFIX + require(userId, "userId");
      case CLUSTER ->
          CLUSTER_PREFIX + CLUSTER_USER_REQUEST_INDEX_SUFFIX + require(userId, "userId");
    };
  }

  public String userJobSequence(UUID userId) {
    return switch (this) {
      case STANDALONE -> STANDALONE_USER_JOB_SEQUENCE_PREFIX + require(userId, "userId");
      case CLUSTER -> CLUSTER_PREFIX + CLUSTER_USER_JOB_SEQUENCE_SUFFIX + require(userId, "userId");
    };
  }

  public String userRequestJobSequence(UUID userId, UUID requestId) {
    return userJobSequence(userId) + REQUEST_SUFFIX + require(requestId, "requestId");
  }

  public String activeRequestKey(UUID userId) {
    return admissionUserPrefix(userId) + ACTIVE_REQUEST_SUFFIX;
  }

  public String activeRunningKey(UUID userId) {
    return admissionUserPrefix(userId) + ACTIVE_RUNNING_SUFFIX;
  }

  public String activeBacklogKey(UUID userId) {
    return admissionUserPrefix(userId) + ACTIVE_BACKLOG_SUFFIX;
  }

  public String replacementRequestKey(UUID userId) {
    return admissionUserPrefix(userId) + REPLACEMENT_REQUEST_SUFFIX;
  }

  public String replacementJobsKey(UUID userId) {
    return admissionUserPrefix(userId) + REPLACEMENT_JOBS_SUFFIX;
  }

  public String activeRunningScanPattern() {
    return switch (this) {
      case STANDALONE -> STANDALONE_ACTIVE_RUNNING_SCAN_PATTERN;
      case CLUSTER -> CLUSTER_ACTIVE_RUNNING_SCAN_PATTERN;
    };
  }

  public String jobMeta(UUID jobId) {
    return jobMeta(require(jobId, "jobId").toString());
  }

  public String jobMeta(String jobId) {
    Objects.requireNonNull(jobId, "jobId");
    return switch (this) {
      case STANDALONE -> STANDALONE_JOB_META_PREFIX + jobId;
      case CLUSTER -> CLUSTER_PREFIX + CLUSTER_JOB_META_PREFIX + jobId;
    };
  }

  /** Returns the address and storage shape used for a queued job payload. */
  public QueuedPayloadAddress queuedPayload(UUID userId, UUID jobId) {
    UUID requiredUserId = require(userId, "userId");
    UUID requiredJobId = require(jobId, "jobId");
    return switch (this) {
      case STANDALONE ->
          new QueuedPayloadAddress(
              this,
              requiredUserId,
              requiredJobId,
              StorageKind.PER_JOB_STRING,
              STANDALONE_JOB_PAYLOAD_PREFIX + requiredJobId);
      case CLUSTER ->
          new QueuedPayloadAddress(
              this,
              requiredUserId,
              requiredJobId,
              StorageKind.USER_HASH,
              admissionUserPrefix(requiredUserId) + CLUSTER_QUEUED_PAYLOAD_SUFFIX);
    };
  }

  /** Returns the queued-payload Redis key for a UUID or a raw job token from a Redis list. */
  public String queuedPayloadKey(UUID userId, String rawJobId) {
    Objects.requireNonNull(rawJobId, "rawJobId");
    return switch (this) {
      case STANDALONE -> queuedPayloadKey(rawJobId);
      case CLUSTER ->
          admissionUserPrefix(require(userId, "userId")) + CLUSTER_QUEUED_PAYLOAD_SUFFIX;
    };
  }

  /** Returns the standalone per-job payload key for a raw job token. */
  public String queuedPayloadKey(String rawJobId) {
    if (this != STANDALONE) {
      throw new IllegalStateException("Cluster queued payloads require a user identity");
    }
    return STANDALONE_JOB_PAYLOAD_PREFIX + Objects.requireNonNull(rawJobId, "rawJobId");
  }

  private String admissionUserPrefix(UUID userId) {
    return switch (this) {
      case STANDALONE -> STANDALONE_ADMISSION_USER_PREFIX + require(userId, "userId");
      case CLUSTER -> CLUSTER_PREFIX + CLUSTER_ADMISSION_USER_PREFIX + require(userId, "userId");
    };
  }

  private static <T> T require(T value, String name) {
    return Objects.requireNonNull(value, name);
  }

  /** Identifies how the queued payload is represented in Redis. */
  public enum StorageKind {
    /** Stores each payload in a per-job Redis string with native key expiry. */
    PER_JOB_STRING,

    /** Stores payload/deadline pairs in a user hash checked against Redis time. */
    USER_HASH
  }

  /** Immutable selected address for one queued job payload. */
  public record QueuedPayloadAddress(
      HistoricalJobControlKeyLayout layout,
      UUID userId,
      UUID jobId,
      StorageKind storageKind,
      String redisKey) {

    public QueuedPayloadAddress {
      Objects.requireNonNull(layout, "layout");
      Objects.requireNonNull(userId, "userId");
      Objects.requireNonNull(jobId, "jobId");
      Objects.requireNonNull(storageKind, "storageKind");
      Objects.requireNonNull(redisKey, "redisKey");
      var expectedStorageKind =
          layout == STANDALONE ? StorageKind.PER_JOB_STRING : StorageKind.USER_HASH;
      var expectedRedisKey =
          layout == STANDALONE
              ? STANDALONE_JOB_PAYLOAD_PREFIX + jobId
              : layout.admissionUserPrefix(userId) + CLUSTER_QUEUED_PAYLOAD_SUFFIX;
      if (storageKind != expectedStorageKind || !redisKey.equals(expectedRedisKey)) {
        throw new IllegalArgumentException(
            "Queued payload address does not match the selected historical control layout");
      }
    }

    /** Field containing the serialized payload when the selected storage is a user hash. */
    public String payloadField() {
      requireHash();
      return jobId.toString();
    }

    /** Field containing the absolute expiry deadline when the selected storage is a user hash. */
    public String deadlineField() {
      requireHash();
      return jobId + DEADLINE_SUFFIX;
    }

    private void requireHash() {
      if (storageKind != StorageKind.USER_HASH) {
        throw new IllegalStateException("Standalone queued payloads are per-job strings");
      }
    }
  }
}
