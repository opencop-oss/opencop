// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    schema = DeadLetterAdminContextSchema.SCHEMA,
    name = DeadLetterAdminContextSchema.RECORD_TABLE)
class ProcessorDeadLetterRecordEntity {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeadLetterRecord.State state;

  @Column(name = "state_updated_at", nullable = false)
  private Instant stateUpdatedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DeadLetterRecord.Stage stage;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_kind", nullable = false)
  private DeadLetterRecord.FailureKind failureKind;

  @Column(name = "original_topic", nullable = false)
  private String originalTopic;

  @Column(name = "original_partition")
  private Integer originalPartition;

  @Column(name = "original_offset")
  private Long originalOffset;

  @Column(name = "dlt_topic", nullable = false)
  private String dltTopic;

  @Column(name = "dlt_partition", nullable = false)
  private int dltPartition;

  @Column(name = "dlt_offset", nullable = false)
  private long dltOffset;

  @Column(name = "exception_class", nullable = false)
  private String exceptionClass;

  @Column(name = "exception_message", nullable = false)
  private String exceptionMessage;

  @Column(name = "replay_count", nullable = false)
  private int replayCount;

  @Column(name = "context_json", nullable = false)
  private String contextJson;

  @Column(name = "headers_json", nullable = false)
  private String headersJson;

  @Column(name = "payload_ref", nullable = false)
  private String payloadRef;

  @Column(name = "payload_sha256")
  private String payloadSha256;

  @Column(name = "payload_size_bytes")
  private Long payloadSizeBytes;

  @Column(name = "payload_expires_at", nullable = false)
  private Instant payloadExpiresAt;

  @Column(name = "indexed_at", nullable = false)
  private Instant indexedAt;

  protected ProcessorDeadLetterRecordEntity() {}

  static ProcessorDeadLetterRecordEntity from(DeadLetterRecord failure) {
    var entity = new ProcessorDeadLetterRecordEntity();
    entity.id = failure.id();
    entity.state = failure.state();
    entity.stateUpdatedAt = failure.stateUpdatedAt();
    entity.updatedAt = failure.updatedAt();
    entity.stage = failure.stage();
    entity.failureKind = failure.failureKind();
    entity.originalTopic = failure.originalTopic();
    entity.originalPartition = failure.originalPartition();
    entity.originalOffset = failure.originalOffset();
    entity.dltTopic = failure.dltTopic();
    entity.dltPartition = failure.dltPartition();
    entity.dltOffset = failure.dltOffset();
    entity.exceptionClass = failure.exceptionClass();
    entity.exceptionMessage = failure.exceptionMessage();
    entity.replayCount = failure.replayCount();
    entity.contextJson = failure.contextJson();
    entity.headersJson = failure.headersJson();
    entity.payloadRef = failure.payloadRef();
    entity.payloadSha256 = failure.payloadSha256();
    entity.payloadSizeBytes = failure.payloadSizeBytes();
    entity.payloadExpiresAt = failure.payloadExpiresAt();
    entity.indexedAt = failure.indexedAt();
    return entity;
  }

  DeadLetterRecord toRecord() {
    return new DeadLetterRecord(
        id,
        state,
        stateUpdatedAt,
        updatedAt,
        stage,
        failureKind,
        originalTopic,
        originalPartition,
        originalOffset,
        dltTopic,
        dltPartition,
        dltOffset,
        exceptionClass,
        exceptionMessage,
        replayCount,
        contextJson,
        headersJson,
        payloadRef,
        payloadSha256,
        payloadSizeBytes,
        payloadExpiresAt,
        indexedAt,
        DeadLetterRecord.AdminContext.empty());
  }

  UUID getId() {
    return id;
  }

  DeadLetterRecord.State getState() {
    return state;
  }

  Instant getStateUpdatedAt() {
    return stateUpdatedAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  DeadLetterRecord.Stage getStage() {
    return stage;
  }

  DeadLetterRecord.FailureKind getFailureKind() {
    return failureKind;
  }

  String getOriginalTopic() {
    return originalTopic;
  }

  Integer getOriginalPartition() {
    return originalPartition;
  }

  Long getOriginalOffset() {
    return originalOffset;
  }

  String getDltTopic() {
    return dltTopic;
  }

  int getDltPartition() {
    return dltPartition;
  }

  long getDltOffset() {
    return dltOffset;
  }

  String getExceptionClass() {
    return exceptionClass;
  }

  String getExceptionMessage() {
    return exceptionMessage;
  }

  int getReplayCount() {
    return replayCount;
  }

  String getContextJson() {
    return contextJson;
  }

  String getHeadersJson() {
    return headersJson;
  }

  String getPayloadRef() {
    return payloadRef;
  }

  String getPayloadSha256() {
    return payloadSha256;
  }

  Long getPayloadSizeBytes() {
    return payloadSizeBytes;
  }

  Instant getPayloadExpiresAt() {
    return payloadExpiresAt;
  }

  Instant getIndexedAt() {
    return indexedAt;
  }
}
