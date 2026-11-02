// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import java.time.Instant;

public record HistoricalJobAdmissionRecoveryState(
    boolean terminal,
    HistoricalJobStage stage,
    Instant startedAt,
    Instant expiresAt,
    String failureReason) {}
