// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.cache;

import java.util.List;
import java.util.UUID;

public record CacheEventsNotification(UUID sourceId, List<String> objectIds) {}
