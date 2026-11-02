// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.model;

import com.opencop.core.sdk.model.watchbox.WatchboxAlertingStatus;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import java.time.Instant;
import java.util.UUID;

public record WatchboxSourceStatusRecord(
    UUID sourceId,
    WatchboxAlertingStatus status,
    WatchboxStatusReason reason,
    Instant sinceAt,
    Long observedCount,
    Long cap,
    Instant updatedAt) {}
