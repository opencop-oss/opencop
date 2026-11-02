// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.history;

import com.opencop.core.sdk.model.SubjectType;
import java.util.UUID;

/**
 * Redis-backed snapshot for {@code history.job.progress} / {@code history.job.completed} (Slice
 * 14).
 */
public record HistoricalJobDeliverySnapshot(
    UUID targetUserId,
    UUID requestId,
    UUID jobId,
    SubjectType subjectType,
    UUID subjectId,
    long totalChunks,
    long cachedChunks,
    long computedChunks,
    long failedChunks,
    long deliveredChunks,
    boolean executionComplete,
    boolean userLifecycle,
    String failureReason) {}
