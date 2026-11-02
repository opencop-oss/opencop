// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.model.history;

import java.time.Instant;
import java.util.UUID;

public record HistoricalReadCursor(Instant playbackAt, String objectId, UUID eventId) {}
