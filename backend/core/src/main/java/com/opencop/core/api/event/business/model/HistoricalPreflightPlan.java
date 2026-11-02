// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import com.opencop.core.sdk.model.history.HistoricalResolution;
import java.time.Instant;

public record HistoricalPreflightPlan(
    HistoricalResolution resolution, Instant effectiveFrom, Instant effectiveTo, long chunkCount) {}
