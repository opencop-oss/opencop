// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.time.Instant;
import java.util.UUID;

public record WatchboxSourceStatusResponse(
    UUID sourceId,
    String sourceName,
    WatchboxAlertingStatus status,
    WatchboxStatusReason reason,
    Instant sinceAt,
    Long observedCount,
    Long cap,
    Instant updatedAt) {}
