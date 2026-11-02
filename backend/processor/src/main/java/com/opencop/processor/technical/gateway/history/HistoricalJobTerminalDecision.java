// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import java.util.UUID;

public record HistoricalJobTerminalDecision(
    UUID jobId, UUID requestId, HistoricalJobAdmissionDecision admissionDecision) {}
